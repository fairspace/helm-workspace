package nl.fairspace.pluto.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static nl.fairspace.pluto.config.Urls.ICONS_PATH;

@RestController
@Slf4j
@RequiredArgsConstructor
public class IconsResource {

    private final IconsResourceService iconsResourceService;
    /**
     * GET  /api/iconsvg/{icon_name} : returns a svg icon of specified name if exists.
     * If icon does not exist, returns 404.
     * Cache-control header with max-age allows to cache the icon.
     *
     * @return svg icon.
     */
    @GetMapping(value = ICONS_PATH + "{icon_name}", produces = "image/svg+xml")
    public ResponseEntity<byte[]> iconsvg(@PathVariable String icon_name) {
        try (InputStream in = iconsResourceService.getSvgIconInputStream(icon_name)) {
            if (in == null || in.available() == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
                    .body(in.readAllBytes());
        } catch (IOException e) {
            log.error("Error reading image", e);
            return ResponseEntity.notFound().build();
        }
    }
}
