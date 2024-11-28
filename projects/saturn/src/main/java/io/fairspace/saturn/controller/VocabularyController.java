package io.fairspace.saturn.controller;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.fairspace.saturn.services.metadata.Serialization.getFormat;
import static io.fairspace.saturn.services.metadata.Serialization.serialize;

@RestController
@RequestMapping("/vocabulary")
public class VocabularyController {

    private final Model vocabulary;

    public VocabularyController(@Qualifier("vocabulary") Model vocabulary) {
        this.vocabulary = vocabulary;
    }

    @GetMapping("/")
    public ResponseEntity<String> getVocabulary(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {
        var format = getFormat(acceptHeader);
        var contentType = format.getLang().getHeaderString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        var serializedVocabulary = serialize(vocabulary, format);
        return new ResponseEntity<>(serializedVocabulary, headers, HttpStatus.OK);
    }
}
