package io.fairspace.saturn.webdav;

import java.util.HashMap;
import java.util.Map;

import io.milton.http.FileItem;
import io.milton.http.RequestParseException;
import io.milton.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import io.fairspace.saturn.webdav.blobstore.BlobFileItem;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.milton.http.ResourceHandlerHelper.ATT_NAME_FILES;
import static io.milton.http.ResourceHandlerHelper.ATT_NAME_PARAMS;
import static java.util.stream.Collectors.toMap;

public class PreParsedServletRequest extends ServletRequest {

    public PreParsedServletRequest(HttpServletRequest request, BlobStore store) throws RequestParseException {
        super(request, request.getServletContext());

        var params = new HashMap<String, String>();
        Map<String, FileItem> files = new HashMap<>();
        super.parseRequestParameters(params, files);

        if ("upload_files".equals(params.get("action"))) {
            files = files.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> new BlobFileItem(e.getValue(), store)));
        }

        getAttributes().put(ATT_NAME_PARAMS, params);
        getAttributes().put(ATT_NAME_FILES, files);
    }

    @Override
    public void parseRequestParameters(Map<String, String> params, Map<String, FileItem> files)
            throws RequestParseException {
        params.putAll(getParams());
        files.putAll(getFiles());
    }
}
