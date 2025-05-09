package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Service
public class RewardsService {
	private static final Logger log = LoggerFactory.getLogger(RewardsService.class);
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private static final int DEFAULT_PROXIMITY_BUFFER = 10;
	private static int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;
	private static final int ATTRACTION_PROXIMITY_RANGE = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ForkJoinPool forkJoinPool;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		// Init threads pool size
		int processors = Runtime.getRuntime().availableProcessors();
		log.info("Available processors: {}", processors);

		// Minimum pool size of 50 threads (for CICD)
		int parallelism = Math.max(50, processors * 10);

		forkJoinPool = new ForkJoinPool(parallelism);
		log.info("Initialized ForkJoinPool with parallelism: {}", parallelism);
	}
	
	public static void setProximityBuffer(int proximityBuffer) {
		RewardsService.proximityBuffer = proximityBuffer;
	}
	
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

		Set<String> rewardedAttractions = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionName)
				.collect(Collectors.toSet());

		for (VisitedLocation visitedLocation : userLocations) {
			attractions.stream()
					// Filter out attractions already rewarded
					.filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
					// Filter attractions near user's visited location
					.filter(attraction -> nearAttraction(visitedLocation, attraction))
					// Calculate rewards and add them to the user
					.forEach(attraction -> {
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
					});
		}
	}

	public void calculateMultipleUserRewards(List<User> users) {
		forkJoinPool.submit(() ->
				users.parallelStream().forEach(this::calculateRewards)
		).join();
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return (getDistance(attraction, location) <= ATTRACTION_PROXIMITY_RANGE);
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return (getDistance(attraction, visitedLocation.location) <= proximityBuffer);
	}
	
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}

}
