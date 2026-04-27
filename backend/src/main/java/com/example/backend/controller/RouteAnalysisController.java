package com.example.backend.controller;

import com.example.backend.dto.ModelInfoDto;
import com.example.backend.dto.PredictionRequestDto;
import com.example.backend.dto.PredictionResponseDto;
import com.example.backend.dto.RouteAnalysisResponseDto;
import com.example.backend.dto.RouteRequestDto;
import com.example.backend.dto.RouteResponseDto;
import com.example.backend.ml.MlServiceClient;
import com.example.backend.routing.FeatureEngineeringService;
import com.example.backend.routing.GeocodingService;
import com.example.backend.routing.OsrmRoutingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RouteAnalysisController {

    private static final String SYNTHETIC_DISCLAIMER =
            "Synthetic Denmark demo prediction. The model trains on generated data and does not reflect real road truth.";

    private final GeocodingService geocodingService;
    private final OsrmRoutingService routingService;
    private final FeatureEngineeringService featureEngineeringService;
    private final MlServiceClient mlServiceClient;

    public RouteAnalysisController(
            GeocodingService geocodingService,
            OsrmRoutingService routingService,
            FeatureEngineeringService featureEngineeringService,
            MlServiceClient mlServiceClient
    ) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.featureEngineeringService = featureEngineeringService;
        this.mlServiceClient = mlServiceClient;
    }

    @PostMapping("/route")
    public RouteResponseDto route(@Valid @RequestBody RouteRequestDto request) {
        var origin = geocodingService.geocode(request.origin());
        var destination = geocodingService.geocode(request.destination());
        return routingService.toDto(routingService.computeRoute(origin, destination));
    }

    @PostMapping("/predict")
    public PredictionResponseDto predict(@Valid @RequestBody PredictionRequestDto request) {
        return mlServiceClient.predict(request);
    }

    @PostMapping("/route-and-predict")
    public RouteAnalysisResponseDto routeAndPredict(@Valid @RequestBody RouteRequestDto request) {
        var origin = geocodingService.geocode(request.origin());
        var destination = geocodingService.geocode(request.destination());
        var route = routingService.computeRoute(origin, destination);
        var features = featureEngineeringService.deriveFeatures(route, request.selectedDepartureTime());
        var prediction = mlServiceClient.predict(new PredictionRequestDto(features));

        return new RouteAnalysisResponseDto(
                routingService.toDto(route),
                features,
                prediction,
                true,
                SYNTHETIC_DISCLAIMER
        );
    }

    @GetMapping("/model-info")
    public ModelInfoDto modelInfo() {
        return mlServiceClient.getModelInfo();
    }

    @PostMapping("/retrain-model")
    public ModelInfoDto retrainModel() {
        return mlServiceClient.retrain();
    }
}
