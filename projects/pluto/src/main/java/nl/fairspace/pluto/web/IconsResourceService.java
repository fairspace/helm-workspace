package nl.fairspace.pluto.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.fairspace.pluto.config.dto.PlutoConfig;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static nl.fairspace.pluto.config.Urls.ICONS_PATH;

@Slf4j
@Service
@RequiredArgsConstructor
public class IconsResourceService {

    private final PlutoConfig plutoConfig;

    public String getIconUrl(String name) {
        if (plutoConfig.getIcons().containsKey(name)) {
            return String.format("%s%s", ICONS_PATH, name);
        }
        return null;
    }

    public InputStream getSvgIconInputStream(String iconName) {
        String iconPath = plutoConfig.getIcons().get(iconName);
        if (iconPath == null) {
            return null;
        }
        try {
            InputStream inputStream = getClass().getResourceAsStream(iconPath);
            if (inputStream != null && inputStream.available() > 0) {
                return inputStream;
            }
            return new FileInputStream(iconPath);
        } catch (IOException e) {
            log.warn("Icon file not found in : {}", iconPath);
            return null;
        }
    }
}
