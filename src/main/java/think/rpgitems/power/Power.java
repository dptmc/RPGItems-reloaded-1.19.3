package think.rpgitems.power;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import think.rpgitems.item.RPGItem;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base interface for all powers
 */
public interface Power {
    /**
     * Loads configuration for this power
     *
     * @param s Configuration
     */
    void init(ConfigurationSection s);

    /**
     * Saves configuration for this power
     *
     * @param s Configuration
     */
    void save(ConfigurationSection s);

    /**
     * NamespacedKey of this power
     *
     * @return namespacedKey
     */
    NamespacedKey getNamespacedKey();

    /**
     * Name of this power
     *
     * @return name
     */
    String getName();

    /**
     * Display text of this power
     *
     * @return Display text
     */
    String displayText();

    /**
     * Item it belongs to
     */
    RPGItem getItem();

    void setItem(RPGItem item);

    Set<TriggerType> getTriggers();

    Set<String> getSelectors();

    Set<String> getConditions();

    default void deinit() {
    }

    @SuppressWarnings("unchecked")
    static Set<TriggerType> getTriggerTypes(Class<? extends Power> cls) {
        return TypeToken.of(cls).getTypes().interfaces().stream()
                        .map(TypeToken::getRawType)
                        .filter(Power.class::isAssignableFrom)
                        .filter(i -> !Objects.equals(i, Power.class))
                        .map(i -> TriggerType.fromInterface((Class<? extends Power>) i))
                        .collect(Collectors.toSet());
    }

    static Set<TriggerType> getDefaultTriggerTypes(Class<? extends Power> cls) {
        PowerMeta annotation = cls.getAnnotation(PowerMeta.class);
        if (annotation != null) {
            if (annotation.defaultTrigger().length > 0) {
                return Sets.newHashSet(annotation.defaultTrigger());
            }
            if (annotation.marker()) {
                return Collections.emptySet();
            }
        }
        return getTriggerTypes(cls);
    }
}
