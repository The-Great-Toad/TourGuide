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
import java.util.concurrent.ForkJoinPool;

@Service
public class RewardsService {
	private static final Logger log = LoggerFactory.getLogger(RewardsService.class);
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private static final int defaultProximityBuffer = 10;
	private static int proximityBuffer = defaultProximityBuffer;
	private static final int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ForkJoinPool forkJoinPool;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		int processors = Runtime.getRuntime().availableProcessors();
//		int poolSize = processors * 10;
		// Minimum pool size of 50 threads (for CICD)
		int poolSize = Math.max(50, processors * 10);
		log.info("Available processors: {}", processors);
		forkJoinPool = new ForkJoinPool(poolSize);
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		RewardsService.proximityBuffer = proximityBuffer;
	}
	
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();
		
		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if(user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
					if(nearAttraction(visitedLocation, attraction)) {
						user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
					}
				}
			}
		}
	}

	public void calculateMultipleUserRewards(List<User> users) {
		forkJoinPool.submit(() ->
				users.parallelStream().forEach(this::calculateRewards)
		).join();
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return (getDistance(attraction, location) <= attractionProximityRange);
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
