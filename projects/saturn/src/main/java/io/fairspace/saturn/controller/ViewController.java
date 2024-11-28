package io.fairspace.saturn.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.controller.dto.CountDto;
import io.fairspace.saturn.controller.dto.FacetsDto;
import io.fairspace.saturn.controller.dto.ViewPageDto;
import io.fairspace.saturn.controller.dto.ViewsDto;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;
import io.fairspace.saturn.services.views.QueryService;
import io.fairspace.saturn.services.views.ViewService;

@RestController
@RequestMapping("/views")
@Validated
public class ViewController {

    private final ViewService viewService;

    private final QueryService services;

    public ViewController(ViewService viewService, @Qualifier("queryService") QueryService services) {
        this.viewService = viewService;
        this.services = services;
    }

    @GetMapping("/")
    public ResponseEntity<ViewsDto> getViews() {
        var views = viewService.getViews();
        return ResponseEntity.ok(new ViewsDto(views));
    }

    @PostMapping("/")
    public ResponseEntity<ViewPageDto> getViewData(@Valid @RequestBody ViewRequest requestBody) {
        var result = services.retrieveViewPage(requestBody);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/facets")
    public ResponseEntity<FacetsDto> getFacets() {
        var facets = viewService.getFacets();
        return ResponseEntity.ok(new FacetsDto(facets));
    }

    @PostMapping("/count")
    public ResponseEntity<CountDto> count(@Valid @RequestBody CountRequest requestBody) {
        var result = services.count(requestBody);
        return ResponseEntity.ok(result);
    }
}
