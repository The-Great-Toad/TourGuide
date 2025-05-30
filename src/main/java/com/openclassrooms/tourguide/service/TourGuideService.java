package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.dto.user.User;
import com.openclassrooms.tourguide.dto.user.UserReward;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

@Service
public class TourGuideService {
	private static final Logger logger = LoggerFactory.getLogger(TourGuideService.class);

	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer;
	public final Tracker tracker;
	private final ForkJoinPool forkJoinPool;
	boolean testMode = true;
	public final Random random;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.tripPricer = new TripPricer();
		this.forkJoinPool = initForkJoinPool();
		this.random = new SecureRandom();

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

	/**
	 * Initializes the ForkJoinPool with a size based on the number of available processors.
	 *
	 * @return A ForkJoinPool instance with the specified parallelism.
	 */
	private ForkJoinPool initForkJoinPool() {
		int processors = Runtime.getRuntime().availableProcessors();
		int poolSize = processors * 10;
		return new ForkJoinPool(poolSize);
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
        return user.getVisitedLocations().isEmpty() ?
				trackUserLocation(user) :
				user.getLastVisitedLocation();
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
				TEST_SERVER_API_KEY,
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
	 * Track the location of multiple users in parallel using ForkJoinPool
	 *
	 * @param users The users to track
	 * @return The list of visited locations for each user
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
					nearbyAttractions.remove(nearbyAttractions
							.stream()
							.max(Comparator.comparing(NearbyAttractionDTO::getDistance))
							.get());
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

	/* *********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String TEST_SERVER_API_KEY = "test-server-api-key";
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
		logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
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
		return leftLimit + random.nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + random.nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(random.nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
