package io.fairspace.saturn.webdav.resources;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.CollectionResource;
import io.milton.resource.Resource;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.Access;
import io.fairspace.saturn.webdav.DavFactory;

import static io.fairspace.saturn.webdav.DavFactory.childSubject;

import static io.milton.http.ResponseStatus.SC_FORBIDDEN;

@Log4j2
public class ExtraStorageRootResource extends RootResource {

    private final WebDavProperties webDavProperties;

    public ExtraStorageRootResource(DavFactory factory, WebDavProperties webDavProperties) {
        super(factory);
        this.webDavProperties = webDavProperties;
    }

    @Override
    public List<? extends Resource> getChildren() {
        return factory.rootSubject
                .getModel()
                .listSubjectsWithProperty(RDF.type, FS.ExtraStorageDirectory)
                .mapWith(factory::getResource)
                .filterDrop(Objects::isNull)
                .toList();
    }

    @Override
    public CollectionResource createCollection(String name)
            throws ConflictException, BadRequestException, NotAuthorizedException {
        if (!webDavProperties.getExtraStorage().getDefaultRootCollections().contains(name)) {
            // Currently all root extra storage directories should be specified in the extra storage config
            throw new NotAuthorizedException(
                    String.format("Directory with name %s not specified in the configuration.", name),
                    this,
                    SC_FORBIDDEN);
        }

        if (name != null) {
            name = name.trim();
        }
        validateTargetCollectionName(name);
        var existing = findStorageWithName(name);
        if (existing.isPresent()) {
            return (CollectionResource) existing.get();
        }

        var subj = childSubject(factory.rootSubject, name);
        subj.getModel().removeAll(subj, null, null).removeAll(null, null, subj);
        subj.addProperty(RDF.type, FS.ExtraStorageDirectory).addProperty(RDFS.label, name);

        return (CollectionResource) factory.getResource(subj, Access.Manage);
    }

    @Override
    public void validateTargetCollectionName(String name) throws BadRequestException {
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("The storage name is empty.");
        }
    }

    private Optional<Resource> findStorageWithName(String name) {
        return factory.rootSubject
                .getModel()
                .listSubjects()
                .filterDrop(Objects::isNull)
                .filterKeep(r -> r.getProperty(RDFS.label) != null
                        && r.getProperty(RDFS.label).getString().equals(name))
                .mapWith(factory::getResource)
                .nextOptional();
    }
}
