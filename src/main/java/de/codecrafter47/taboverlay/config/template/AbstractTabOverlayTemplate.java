package de.codecrafter47.taboverlay.config.template;

import de.codecrafter47.taboverlay.config.expression.template.ExpressionTemplate;
import de.codecrafter47.taboverlay.config.template.text.TextTemplate;
import de.codecrafter47.taboverlay.config.view.AbstractActiveElement;
import de.codecrafter47.taboverlay.handler.TabOverlayHandler;
import lombok.Data;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Data
public abstract class AbstractTabOverlayTemplate {

    private Path path;

    private ExpressionTemplate viewerPredicate;

    private int priority;

    @Nullable
    private List<TextTemplate> header;

    private float headerAnimationUpdateInterval = Float.NaN;

    @Nullable
    private List<TextTemplate> footer;

    private float footerAnimationUpdateInterval = Float.NaN;

    private Map<String, PlayerSetTemplate> playerSets;

    public boolean showHeaderAndFooter() {
        return getHeader() != null || getFooter() != null;
    }

    public abstract AbstractActiveElement<?> createContentView(TabOverlayHandler handler);

}
