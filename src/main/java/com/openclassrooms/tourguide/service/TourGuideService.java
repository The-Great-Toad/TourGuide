package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.*;
import com.openclassrooms.tourguide.tracker.*;
import com.openclassrooms.tourguide.user.*;
import gpsUtil.*;
import gpsUtil.location.*;
import org.slf4j.*;
import org.springframework.stereotype.*;
import tripPricer.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.*;

@Service
public class TourGuideService {
	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	private final ForkJoinPool forkJoinPool;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		int processors = Runtime.getRuntime().availableProcessors();
		int poolSize = processors * 10;
		forkJoinPool = new ForkJoinPool(poolSize);

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
        return (user.getVisitedLocations().isEmpty()) ? trackUserLocation(user)
				: user.getLastVisitedLocation();
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulativeRewardPoints = user.getUserRewards().stream().mapToInt(UserReward::getRewardPoints).sum();
		List<Provider> providers = tripPricer.getPrice(
				tripPricerApiKey,
				user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(),
				user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(),
				cumulativeRewardPoints
		);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Track the location of multiple users
	 *
	 * @param users The users
	 * @return The list of visited locations
	 */
	public List<VisitedLocation> trackMultipleUserLocations(List<User> users) {
		return forkJoinPool.submit(() ->
				users.parallelStream()
						.map(this::trackUserLocation)
						.toList()
				).join();
	}

	/**
	 * Get the top 5 nearby attractions for a user based on their last visited location
	 *
	 * @param user The user
	 * @return The top 5 nearby attractions
	 */
	public List<NearbyAttractionDTO> getTopFiveNearbyAttractions(User user) {
		VisitedLocation lastVisitedLocation = getUserLocation(user);
		List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();

		for (Attraction attraction : gpsUtil.getAttractions()) {
			Location attractionLocation = new Location(attraction.longitude, attraction.latitude);
			double distance = rewardsService.getDistance(attractionLocation, lastVisitedLocation.location);

			/* If there are less than 5 attractions or the distance is less than the max distance */
			if (nearbyAttractions.size() < 5 || nearbyAttractions.stream().anyMatch(a -> a.getDistance() > distance)) {
				/* If there are already 5 attractions, remove the one with the max distance */
				if (nearbyAttractions.size() == 5) {
					nearbyAttractions.remove(nearbyAttractions.stream().max(Comparator.comparing(NearbyAttractionDTO::getDistance)).get());
				}
				nearbyAttractions.add(
						new NearbyAttractionDTO(
								attraction.attractionName,
								attractionLocation,
								lastVisitedLocation.location,
								distance,
								rewardsService.getRewardPoints(attraction, user)
						)
				);
			}
		}

		/* Sort nearby attractions by distance */
		nearbyAttractions.sort(Comparator.comparing(NearbyAttractionDTO::getDistance));
		return nearbyAttractions;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(tracker::stopTracking));
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i ->
			user.addToVisitedLocations(
					new VisitedLocation(
							user.getUserId(),
							new Location(generateRandomLatitude(), generateRandomLongitude()),
							getRandomTime()
					)
			)
		);
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
