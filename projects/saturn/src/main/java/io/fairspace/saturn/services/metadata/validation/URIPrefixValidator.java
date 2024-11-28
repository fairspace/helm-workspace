package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static io.fairspace.saturn.config.WebDAVConfig.WEB_DAV_URL_PATH;

@Component
public class URIPrefixValidator implements MetadataRequestValidator {

    private final String restrictedPrefix;

    public URIPrefixValidator(@Value("${application.publicUrl}") String publicUrl) {
        this.restrictedPrefix = publicUrl + WEB_DAV_URL_PATH; // should be the same as dav root unique id
    }

    @Override
    public void validate(Model before, Model after, Model removed, Model added, ViolationHandler violationHandler) {
        added.listSubjects()
                .filterKeep(Resource::isURIResource)
                .filterDrop(resource -> before.contains(resource, null))
                .filterKeep(resource -> resource.getURI().startsWith(restrictedPrefix))
                .forEachRemaining(resource -> violationHandler.onViolation(
                        "Cannot add resource with URI starting with restricted prefix '" + restrictedPrefix + "'.",
                        resource,
                        null,
                        null));
    }
}
