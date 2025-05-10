package com.openclassrooms.tourguide.tracker;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.dto.user.User;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Tracker extends Thread {
	private final Logger logger = LoggerFactory.getLogger(Tracker.class);
	private static final long TRACKING_POLLING_INTERVAL = TimeUnit.MINUTES.toSeconds(5);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final TourGuideService tourGuideService;
	private boolean stop = false;

	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;

		executorService.submit(this);
	}

	/**
	 * Assures to shut down the Tracker thread
	 */
	public void stopTracking() {
		stop = true;
		executorService.shutdownNow();
	}

	@Override
	public void run() {
		StopWatch stopWatch = new StopWatch();
		while (true) {
			if (Thread.currentThread().isInterrupted() || stop) {
				logger.debug("Tracker stopping");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking {} users.", users.size());
			stopWatch.start();
			users.forEach(tourGuideService::trackUserLocation);
			stopWatch.stop();
			logger.atLevel(Level.DEBUG)
					.log("Tracker Time Elapsed: {}", TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
			stopWatch.reset();
			try {
				logger.debug("Tracker sleeping");
				TimeUnit.SECONDS.sleep(TRACKING_POLLING_INTERVAL);
			} catch (InterruptedException e) {
				break;
			}
		}

	}
}
