package de.codecrafter47.taboverlay.config.dsl;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.codecrafter47.data.api.DataHolder;
import de.codecrafter47.data.api.TypeToken;
import de.codecrafter47.taboverlay.config.SortingRulePreprocessor;
import de.codecrafter47.taboverlay.config.context.Context;
import de.codecrafter47.taboverlay.config.dsl.yaml.MarkedPropertyBase;
import de.codecrafter47.taboverlay.config.expression.DefaultExpressionEngine;
import de.codecrafter47.taboverlay.config.expression.ExpressionUpdateListener;
import de.codecrafter47.taboverlay.config.expression.ToBooleanExpression;
import de.codecrafter47.taboverlay.config.placeholder.DataHolderPlaceholderDataProviderSupplier;
import de.codecrafter47.taboverlay.config.placeholder.PlaceholderArg;
import de.codecrafter47.taboverlay.config.placeholder.PlaceholderBuilder;
import de.codecrafter47.taboverlay.config.placeholder.PlaceholderException;
import de.codecrafter47.taboverlay.config.placeholder.UnknownPlaceholderException;
import de.codecrafter47.taboverlay.config.template.PlayerOrderTemplate;
import de.codecrafter47.taboverlay.config.template.TemplateCreationContext;
import de.codecrafter47.taboverlay.util.Unchecked;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

//@RequiredArgsConstructor
public class PlayerOrdersConfiguration extends MarkedPropertyBase {
    public static final PlayerOrderConfiguration DEFAULT = new PlayerOrderConfiguration("name as text asc");

    private static final Set<TypeToken<?>> NUMERIC_TYPES = ImmutableSet.of(TypeToken.INTEGER, TypeToken.FLOAT, TypeToken.DOUBLE, TypeToken.BOOLEAN);
    private static final Set<TypeToken<?>> STRING_TYPES = ImmutableSet.of(TypeToken.STRING);

    private static final Map<String, PlayerOrderTemplate.Direction> DIRECTION_ID_MAP = ImmutableMap.<String, PlayerOrderTemplate.Direction>builder()
            .put("ascending", PlayerOrderTemplate.Direction.ASCENDING)
            .put("asc", PlayerOrderTemplate.Direction.ASCENDING)
            .put("descending", PlayerOrderTemplate.Direction.DESCENDING)
            .put("desc", PlayerOrderTemplate.Direction.DESCENDING)
            .put("viewer-first", PlayerOrderTemplate.Direction.VIEWER_FIRST)
            .put("custom-order", PlayerOrderTemplate.Direction.CUSTOM)
            .put("custom", PlayerOrderTemplate.Direction.CUSTOM)
            .build();

    private static final Map<String, PlayerOrderTemplate.Type> TYPE_ID_MAP = ImmutableMap.<String, PlayerOrderTemplate.Type>builder()
            .put("number", PlayerOrderTemplate.Type.NUMBER)
            .put("text", PlayerOrderTemplate.Type.TEXT)
            .put("string", PlayerOrderTemplate.Type.TEXT)
            .build();

    public String condition;
    public String playerOrder;

    private ToBooleanExpression compiledCondition;

    @Getter
    private PlayerOrderTemplate compiledPlayerOrder;

    void compileCondition(TemplateCreationContext tcc) {
        val options = DefaultExpressionEngine.Options.builder()
                .withDefaultTokenReaders()
                .withDefaultValueReaders()
                .withDefaultOperators()
                .build();
        val expressionEngine = new DefaultExpressionEngine(options);
        compiledCondition = expressionEngine.compile(tcc, condition, getStartMark()).instantiateWithBooleanResult();
    }

    public boolean matches(Context context) {
        compiledCondition.activate(context, () -> {});
        boolean result = compiledCondition.evaluate();
        compiledCondition.deactivate();
        return result;
    }

    void compileTemplate(TemplateCreationContext tcc) {
        SortingRulePreprocessor preprocessor = tcc.getSortingRulePreprocessor();

        List<PlayerOrderTemplate.Entry> chain = new ArrayList<>();

        if (playerOrder != null) {
            String[] elements = playerOrder.split(",");
            for (String element : elements) {

                if (preprocessor != null) {
                    element = preprocessor.process(element, tcc.getErrorHandler(), getStartMark());
                }

                String[] tokens = element.trim().split(" ");

                if (tokens.length == 0) {
                    tcc.getErrorHandler().addWarning("Player Order contains empty entry. Too many `,`'s?", getStartMark());
                    continue;
                }

                List<PlaceholderArg> args = new ArrayList<>();
                for (String token : tokens) {
                    args.add(new PlaceholderArg.Text(token));
                }

                DataHolderPlaceholderDataProviderSupplier<DataHolder, ?, ?> dataHolderPlaceholder;
                try {
                    if (args.size() >= 2
                            && args.get(0).getText().equals("server")
                            && ("as".equals(args.get(1).getText())
                            || DIRECTION_ID_MAP.containsKey(args.get(1).getText()))) {
                        val arg = args.remove(0);
                        try {
                            val builderPlayer = tcc.getPlayerPlaceholderResolver().resolve(PlaceholderBuilder.create().transformContext(Context::getPlayer), new ArrayList<>(Collections.singletonList(arg)), tcc);
                            val dataProviderFactory = Unchecked.cast(builderPlayer.getDataProviderFactory());
                            dataHolderPlaceholder = Unchecked.cast(dataProviderFactory);
                        } catch (UnknownPlaceholderException | PlaceholderException ex) {
                            // this doesn't happen
                            // if it does there's a bug in the code
                            throw new AssertionError();
                        }
                    } else {
                        PlaceholderBuilder<?, ?> builderPlayer = tcc.getPlayerPlaceholderResolver().resolve(PlaceholderBuilder.create().transformContext(Context::getPlayer), args, tcc);
                        val dataProviderFactory = Unchecked.cast(builderPlayer.getDataProviderFactory());
                        if (dataProviderFactory instanceof DataHolderPlaceholderDataProviderSupplier) {
                            dataHolderPlaceholder = Unchecked.cast(dataProviderFactory);
                        } else {
                            tcc.getErrorHandler().addWarning("Unsuitable placeholder in playerOrder option: `" + Joiner.on(' ').join(Arrays.asList(tokens).subList(0, tokens.length - args.size())) + "`. This placeholder cannot be used for sorting.", getStartMark());
                            continue;
                        }
                    }
                } catch (UnknownPlaceholderException e) {
                    tcc.getErrorHandler().addWarning("Unknown placeholder in playerOrder option: `" + Joiner.on(' ').join(Arrays.asList(tokens)) + "`", getStartMark());
                    continue;
                } catch (PlaceholderException e) {
                    String message = "Error in placeholder in playerOrder option: `" + Joiner.on(' ').join(Arrays.asList(tokens)) + "`:\n" + e.getMessage();
                    if (e.getCause() != null) {
                        message = message + "\nCaused by: " + e.getCause().getMessage();
                    }
                    tcc.getErrorHandler().addWarning(message, getStartMark());
                    continue;
                }

                PlayerOrderTemplate.Direction direction = null;
                PlayerOrderTemplate.Type type = null;
                List<String> customOrder = null;

                for (int i = 0; i < args.size(); i++) {
                    PlaceholderArg arg = args.get(i);
                    String token = arg.getText();

                    if (DIRECTION_ID_MAP.containsKey(token)) {
                        // it's a direction-id
                        if (direction != null) {
                            tcc.getErrorHandler().addWarning("In playerOrder: Ignoring option `" + token + "` for `" + element + "` because direction has already been set.", getStartMark());
                            continue;
                        }
                        direction = DIRECTION_ID_MAP.get(token);

                        if (direction == PlayerOrderTemplate.Direction.CUSTOM) {
                            customOrder = new ArrayList<>();
                            while (i + 1 < args.size() &&
                                    !("as".equals(args.get(i + 1).getText())
                                            || DIRECTION_ID_MAP.containsKey(args.get(i + 1).getText()))) {
                                customOrder.add(args.get(++i).getText());
                            }
                        }
                    } else if (token.equals("as")) {
                        if (++i == args.size()) {
                            tcc.getErrorHandler().addWarning("In playerOrder: In `" + element + "` the `as` needs to be followed by `text` or `number`.", getStartMark());
                            continue;
                        }
                        arg = args.get(i);
                        token = arg.getText();
                        if (!TYPE_ID_MAP.containsKey(token)) {
                            tcc.getErrorHandler().addWarning("In playerOrder: After `" + element + "` encountered unknown type: `as " + token + "`. Try using `as text` or `as number` instead.", getStartMark());
                            continue;
                        }
                        if (type != null) {
                            tcc.getErrorHandler().addWarning("In playerOrder: Ignoring option `as " + token + "` for `" + element + "` because type has already been set.", getStartMark());
                            continue;
                        }
                        type = TYPE_ID_MAP.get(token);
                    } else {
                        tcc.getErrorHandler().addWarning("In playerOrder: Ignoring option `" + token + "` for `" + element + "`. Unknown option.", getStartMark());
                        continue;
                    }
                }

                if (type == null) {
                    // defaults
                    TypeToken<?> placeholderType = dataHolderPlaceholder.getType();
                    if (STRING_TYPES.contains(placeholderType)) {
                        type = PlayerOrderTemplate.Type.TEXT;
                    }
                    if (NUMERIC_TYPES.contains(placeholderType)) {
                        type = PlayerOrderTemplate.Type.NUMBER;
                    }
                }

                if (type == null) {
                    tcc.getErrorHandler().addWarning("In playerOrder: Missing type for `" + element + "`. Try `" + element + " as text` or `" + element + " as number` instead.", getStartMark());
                    continue;
                }

                if (direction == null) {
                    // defaults
                    if (type == PlayerOrderTemplate.Type.TEXT) {
                        direction = PlayerOrderTemplate.Direction.ASCENDING;
                    }
                }

                if (direction == null) {
                    tcc.getErrorHandler().addWarning("In playerOrder: Missing direction for `" + element + "`. Try `" + element + " asc` or `" + element + " desc` instead.", getStartMark());
                    continue;
                }

                chain.add(new PlayerOrderTemplate.Entry(dataHolderPlaceholder, direction, type, customOrder));
            }
        }

        compiledPlayerOrder = new PlayerOrderTemplate(Collections.unmodifiableList(chain));
    }
}
