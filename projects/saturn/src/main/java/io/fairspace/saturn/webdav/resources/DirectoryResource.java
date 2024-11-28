package io.fairspace.saturn.webdav.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.exceptions.NotFoundException;
import io.milton.resource.DeletableCollectionResource;
import io.milton.resource.FolderResource;
import io.milton.resource.Resource;
import org.apache.commons.csv.CSVFormat;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.Access;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.WebDAVServlet;
import io.fairspace.saturn.webdav.blobstore.BlobFileItem;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;

import static io.fairspace.saturn.config.MetadataConfig.METADATA_SERVICE;
import static io.fairspace.saturn.rdf.ModelUtils.getStringProperty;
import static io.fairspace.saturn.webdav.DavFactory.childSubject;
import static io.fairspace.saturn.webdav.PathUtils.encodePath;
import static io.fairspace.saturn.webdav.PathUtils.normalizePath;
import static io.fairspace.saturn.webdav.PathUtils.splitPath;
import static io.fairspace.saturn.webdav.WebDAVServlet.getBlob;
import static io.fairspace.saturn.webdav.WebDAVServlet.setErrorMessage;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;

public class DirectoryResource extends BaseResource implements FolderResource, DeletableCollectionResource {

    private final Model vocabulary;

    public DirectoryResource(
            DavFactory factory,
            org.apache.jena.rdf.model.Resource subject,
            Access access,
            Model userVocabulary,
            Model vocabulary) {
        super(factory, subject, access, userVocabulary);
        this.vocabulary = vocabulary;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return switch (method) {
            case COPY -> access.canRead();
            default -> super.authorise(request, method, auth);
        };
    }

    @Override
    public Date getModifiedDate() {
        return null;
    }

    @Override
    public io.milton.resource.CollectionResource createCollection(String newName)
            throws NotAuthorizedException, ConflictException, BadRequestException {
        var subj = createResource(newName).addProperty(RDF.type, FS.Directory);

        return (io.milton.resource.CollectionResource) factory.getResource(subj, access);
    }

    @Override
    public Resource createNew(String name, InputStream inputStream, Long length, String contentType)
            throws IOException, ConflictException, NotAuthorizedException, BadRequestException {
        return createNew(name, getBlob(), contentType);
    }

    private Resource createNew(String name, BlobInfo blob, String contentType)
            throws NotAuthorizedException, ConflictException, BadRequestException {
        var subj = createResource(name)
                .addProperty(RDF.type, FS.File)
                .addLiteral(FS.currentVersion, 1)
                .addProperty(FS.versions, subject.getModel().createList(newVersion(blob)));

        if (contentType != null) {
            subj.addProperty(FS.contentType, contentType);
        }

        return factory.getResource(subj, access);
    }

    private org.apache.jena.rdf.model.Resource createResource(String name)
            throws ConflictException, NotAuthorizedException, BadRequestException {
        if (name != null) {
            name = name.trim();
        }
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("The name is empty.");
        }
        if (name.contains("\\")) {
            throw new BadRequestException("The name contains an illegal character (\\)");
        }

        var existing = factory.getResourceByType(childSubject(subject, name), access);
        if (existing != null) {
            throw new ConflictException(existing);
        }

        var subj = childSubject(subject, name);
        subj.getModel().removeAll(subj, null, null).removeAll(null, null, subj);
        var t = WebDAVServlet.timestampLiteral();

        subj.addProperty(RDFS.label, name)
                .addProperty(FS.createdBy, factory.currentUserResource())
                .addProperty(FS.dateCreated, t);

        subj.addProperty(FS.belongsTo, subject);
        updateParents(subject);
        return subj;
    }

    @Override
    public Resource child(String childName) throws NotAuthorizedException, BadRequestException {
        return factory.getResource(childSubject(subject, childName), access);
    }

    @Override
    public List<? extends Resource> getChildren() {
        return subject.getModel()
                .listSubjectsWithProperty(FS.belongsTo, subject)
                .mapWith(r -> factory.getResource(r, access))
                .filterDrop(Objects::isNull)
                .toList();
    }

    @Override
    public void delete(boolean purge) throws ConflictException, BadRequestException {
        for (var child : getChildren()) {
            ((BaseResource) child).delete(purge);
        }
        super.delete(purge);
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType)
            throws IOException, NotAuthorizedException, BadRequestException, NotFoundException {}

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return "text/html";
    }

    @Override
    public Long getContentLength() {
        return null;
    }

    @Override
    public boolean isLockedOutRecursive(Request request) {
        return false;
    }

    @Override
    protected void performAction(String action, Map<String, String> parameters, Map<String, FileItem> files)
            throws BadRequestException, NotAuthorizedException, ConflictException {
        switch (action) {
                // curl -i -H 'Authorization: Basic b3JnYW5pc2F0aW9uLWFkbWluOmZhaXJzcGFjZTEyMw==' \
                // -F 'action=upload_files' -F '/dir/subdir/file1.ext=@/dir/subdir/file1.ext' \
                // -F '/dir/subdir/file2.ext=@/dir/subdir/file2.ext' \
                // http://localhost:8080/api/webdav/c1/
            case "upload_files" -> uploadFiles(files);
                // curl -i -H 'Authorization: Basic b3JnYW5pc2F0aW9uLWFkbWluOmZhaXJzcGFjZTEyMw==' \
                // -F 'action=upload_metadata' -F 'file=@meta.csv' http://localhost:8080/api/webdav/c1/
            case "upload_metadata" -> uploadMetadata(files.get("file"));
            case "delete_all_in_directory" -> deleteAllInDirectory();
            default -> super.performAction(action, parameters, files);
        }
    }

    private void uploadFiles(Map<String, FileItem> files)
            throws NotAuthorizedException, ConflictException, BadRequestException {
        for (var entry : files.entrySet()) {
            uploadFile(entry.getKey(), entry.getValue());
        }
    }

    private void deleteAllInDirectory() throws ConflictException, BadRequestException, NotAuthorizedException {
        for (var child : getChildren()) {
            ((BaseResource) child).delete();
        }
    }

    private void uploadFile(String path, FileItem file)
            throws NotAuthorizedException, BadRequestException, ConflictException {
        path = normalizePath(URLDecoder.decode(path, StandardCharsets.UTF_8));
        if (path.contains("/")) {
            var segments = splitPath(path);
            var child = child(segments[0]);
            if (child == null) {
                child = createCollection(segments[0]);
            }
            if (!(child instanceof DirectoryResource)) {
                throw new ConflictException(child);
            }
            var dir = (DirectoryResource) child;
            var relPath = Stream.of(segments).skip(1).collect(joining("/"));
            dir.uploadFile(relPath, file);
        } else {
            var blob = ((BlobFileItem) file).getBlob();
            var child = child(path);
            if (child != null) {
                if (child instanceof FileResource) {
                    ((FileResource) child).replaceContent(blob);
                } else {
                    throw new ConflictException(child);
                }
            } else {
                createNew(path, blob, file.getContentType());
            }
        }
    }

    private void uploadMetadata(FileItem file) throws BadRequestException, ConflictException, NotAuthorizedException {
        if (file == null) {
            setErrorMessage("Missing 'file' parameter");
            throw new BadRequestException(this);
        }
        var model = createDefaultModel();

        try (var is = file.getInputStream();
                var reader = new InputStreamReader(is);
                var csvParser = CSVFormat.DEFAULT
                        .withFirstRecordAsHeader()
                        .withCommentMarker('#')
                        .withIgnoreEmptyLines()
                        .parse(reader)) {
            var headers = new HashSet<>(csvParser.getHeaderNames());
            if (!headers.contains("Path")) {
                setErrorMessage("Line " + csvParser.getCurrentLineNumber()
                        + ". Invalid file format. 'Path' column is missing.");
                throw new BadRequestException(this);
            }
            try {
                for (var record : csvParser) {
                    var path = record.get("Path");
                    org.apache.jena.rdf.model.Resource s;
                    if (path.equals(".") || path.equals("./") || path.equals("/")) {
                        s = subject;
                    } else {
                        if (path.startsWith("./")) {
                            path = path.substring(2);
                        }
                        path = normalizePath(path);
                        s = subject.getModel().createResource(subject + "/" + encodePath(path));
                    }
                    if (!s.getModel().containsResource(s)) {
                        setErrorMessage(
                                "Line " + csvParser.getCurrentLineNumber() + ". File \"" + path + "\" not found");
                        throw new BadRequestException(this);
                    }

                    if (s.hasProperty(FS.dateDeleted)) {
                        setErrorMessage(
                                "Line " + csvParser.getCurrentLineNumber() + ". File \"" + path + "\" was deleted");
                        throw new BadRequestException(this);
                    }

                    var classShape = s.getPropertyResourceValue(RDF.type).inModel(vocabulary);
                    var propertyShapes = new HashMap<String, org.apache.jena.rdf.model.Resource>();

                    classShape
                            .listProperties(SHACLM.property)
                            .mapWith(Statement::getObject)
                            .mapWith(RDFNode::asResource)
                            .filterKeep(propertyShape -> propertyShape.hasProperty(SHACLM.name)
                                    && propertyShape.hasProperty(SHACLM.path)
                                    && propertyShape
                                            .getProperty(SHACLM.path)
                                            .getObject()
                                            .isURIResource())
                            .forEachRemaining(propertyShape -> {
                                var name = getStringProperty(propertyShape, SHACLM.name);
                                if (name != null) {
                                    propertyShapes.put(name, propertyShape);
                                }
                            });

                    for (var header : headers) {
                        if (header.equals("Path")) {
                            continue;
                        }

                        var text = record.get(header);

                        if (isBlank(text)) {
                            continue;
                        }

                        var propertyShape = propertyShapes.get(header);

                        if (propertyShape == null) {
                            setErrorMessage(
                                    "Line " + csvParser.getCurrentLineNumber() + ". Unknown attribute: " + header);
                            throw new BadRequestException(this);
                        }

                        var property = model.createProperty(propertyShape
                                .getPropertyResourceValue(SHACLM.path)
                                .getURI());
                        var datatype = propertyShape.getPropertyResourceValue(SHACLM.datatype);
                        var class_ = propertyShape.getPropertyResourceValue(SHACLM.class_);
                        assert (datatype != null) ^ (class_ != null);

                        var values = text.split("\\|");

                        for (var value : values) {
                            if (class_ != null) {
                                var object = subject.getModel()
                                        .listResourcesWithProperty(RDF.type, class_)
                                        .filterKeep(r -> r.getURI().equals(value) || r.hasProperty(RDFS.label, value))
                                        .toList();
                                if (object.size() == 1) {
                                    model.add(s, property, object.get(0));
                                } else if (object.size() > 1) {
                                    setErrorMessage("Line " + csvParser.getCurrentLineNumber() + ". Object \"" + value
                                            + "\" of class " + "\"" + class_ + "\" is not unique.");
                                    throw new BadRequestException(this);
                                } else {
                                    setErrorMessage("Line " + csvParser.getCurrentLineNumber() + ". Object \"" + value
                                            + "\" of class " + "\"" + class_ + "\" does not exist.");
                                    throw new BadRequestException(this);
                                }
                            } else {
                                var o = model.createTypedLiteral(value, datatype.getURI());
                                model.add(s, property, o);
                            }
                        }
                    }
                }
            } catch (IllegalStateException e) {
                setErrorMessage("Line " + csvParser.getCurrentLineNumber()
                        + ". Metadata file is not a valid comma separated values file (CSV).");
                throw new BadRequestException("Error parsing file " + file.getName(), e);
            }
        } catch (IllegalArgumentException | IOException e) {
            setErrorMessage(e.getMessage());
            throw new BadRequestException("Error parsing file " + file.getName(), e);
        }

        // TODO: This is an old solution, refactor: metadataService is to be a field passed via constructor
        MetadataService metadataService = factory.context.get(METADATA_SERVICE);
        try {
            metadataService.patch(model, Boolean.TRUE);
        } catch (ValidationException e) {
            var message = new StringBuilder("Validation of the uploaded metadata failed.\n");
            var n = 0;
            for (var v : e.getViolations()) {
                var path = v.getSubject().replaceFirst(subject.getURI(), "");
                path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                var propertyShapes = vocabulary.listResourcesWithProperty(SHACLM.path, createURI(v.getPredicate()));
                var propertyName = propertyShapes.hasNext()
                        ? getStringProperty(propertyShapes.next(), SHACLM.name)
                        : createURI(v.getPredicate()).getLocalName();
                message.append("- <")
                        .append(path)
                        .append("> has an invalid value for property *")
                        .append(propertyName)
                        .append("*: ")
                        .append(v.getMessage())
                        .append(".\n");
                if (++n == 5) {
                    if (e.getViolations().size() > n) {
                        message.append("+ ")
                                .append(e.getViolations().size() - n)
                                .append(" more errors.");
                    }
                    break;
                }
            }
            setErrorMessage(message.toString());
            throw new BadRequestException("Error applying metadata", e);
        } catch (Exception e) {
            throw new BadRequestException("Error applying metadata", e);
        }
    }
}
