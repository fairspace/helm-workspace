package io.fairspace.saturn.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.controller.validation.ValidIri;
import io.fairspace.saturn.services.metadata.MetadataService;

import static io.fairspace.saturn.controller.enums.CustomMediaType.APPLICATION_LD_JSON;
import static io.fairspace.saturn.controller.enums.CustomMediaType.APPLICATION_N_TRIPLES;
import static io.fairspace.saturn.controller.enums.CustomMediaType.TEXT_TURTLE;
import static io.fairspace.saturn.services.metadata.Serialization.deserialize;
import static io.fairspace.saturn.services.metadata.Serialization.getFormat;
import static io.fairspace.saturn.services.metadata.Serialization.serialize;

@Log4j2
@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
@Validated
public class MetadataController {

    private static final String DO_VIEWS_UPDATE = "doViewsUpdate";

    public static final String DO_VIEWS_UPDATE_DEFAULT_VALUE = "true";

    private final MetadataService metadataService;

    @GetMapping(
            value = "/",
            produces = {MediaType.APPLICATION_JSON_VALUE, APPLICATION_LD_JSON, TEXT_TURTLE, APPLICATION_N_TRIPLES})
    public ResponseEntity<String> getMetadata(
            @RequestParam(required = false) String subject,
            @RequestParam(name = "withValueProperties", defaultValue = "false") boolean withValueProperties,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {
        var model = metadataService.get(subject, withValueProperties);
        var format = getFormat(acceptHeader);
        var metadata = serialize(model, format);
        return ResponseEntity.ok(metadata);
    }

    @PutMapping(
            value = "/",
            consumes = {MediaType.APPLICATION_JSON_VALUE, APPLICATION_LD_JSON, TEXT_TURTLE, APPLICATION_N_TRIPLES})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putMetadata(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestParam(name = DO_VIEWS_UPDATE, defaultValue = DO_VIEWS_UPDATE_DEFAULT_VALUE)
                    boolean doMaterializedViewsRefresh) {
        Model model = deserialize(body, contentType);
        metadataService.put(model, doMaterializedViewsRefresh);
    }

    @PatchMapping(
            value = "/",
            consumes = {MediaType.APPLICATION_JSON_VALUE, APPLICATION_LD_JSON, TEXT_TURTLE, APPLICATION_N_TRIPLES})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void patchMetadata(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestParam(name = DO_VIEWS_UPDATE, defaultValue = DO_VIEWS_UPDATE_DEFAULT_VALUE) boolean doViewsUpdate) {
        Model model = deserialize(body, contentType);
        metadataService.patch(model, doViewsUpdate);
    }

    @DeleteMapping("/")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMetadata(
            @RequestParam(required = false) @ValidIri String subject,
            @RequestBody(required = false) String body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestParam(name = DO_VIEWS_UPDATE, defaultValue = DO_VIEWS_UPDATE_DEFAULT_VALUE)
                    boolean doMaterializedViewsRefresh) {
        if (subject != null) {
            if (!metadataService.softDelete(ResourceFactory.createResource(subject))) {
                throw new IllegalArgumentException("Subject could not be deleted");
            }
        } else {
            Model model = deserialize(body, contentType);
            metadataService.delete(model, doMaterializedViewsRefresh);
        }
    }
}
