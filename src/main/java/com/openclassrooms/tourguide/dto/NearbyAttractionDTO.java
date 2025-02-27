package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Location;

import java.util.StringJoiner;

public class NearbyAttractionDTO {

    private String attractionName;
    private Location attractionLocation;
    private Location userLocation;
    private double distance;
    private int rewardPoints;

    public NearbyAttractionDTO() {
    }

    public NearbyAttractionDTO(String attractionName, Location attractionLocation, Location userLocation, double distance, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLocation = attractionLocation;
        this.userLocation = userLocation;
        this.distance = distance;
        this.rewardPoints = rewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public Location getAttractionLocation() {
        return attractionLocation;
    }

    public void setAttractionLocation(Location attractionLocation) {
        this.attractionLocation = attractionLocation;
    }

    public Location getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", NearbyAttractionDTO.class.getSimpleName() + "[", "]")
                .add("attractionName='" + attractionName + "'")
                .add("attractionLocation=" + attractionLocation.latitude + "/" + attractionLocation.longitude)
                .add("userLocation=" + userLocation.latitude + "/" + userLocation.longitude)
                .add("distance=" + distance)
                .add("rewardPoints=" + rewardPoints)
                .toString();
    }
}
