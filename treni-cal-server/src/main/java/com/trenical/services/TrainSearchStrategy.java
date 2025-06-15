package com.trenical.services;

import proto.SearchTrainRequest;
import proto.Train;

import java.util.List;

public interface TrainSearchStrategy {
    List<Train> searchTrains(SearchTrainRequest request) throws Exception;
}
