package think.rpgitems.power;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.Pair;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.udojava.evalex.Expression;
import com.udojava.evalex.LazyFunction;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Objective;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import think.rpgitems.AdminCommands;
import think.rpgitems.I18n;
import think.rpgitems.RPGItems;
import think.rpgitems.data.Context;
import think.rpgitems.data.Font;
import think.rpgitems.power.marker.Selector;
import think.rpgitems.power.trigger.Trigger;
import think.rpgitems.utils.MaterialUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    private static LoadingCache<String, List<String>> permissionCache = CacheBuilder
                                                                                .newBuilder()
                                                                                .concurrencyLevel(1)
                                                                                .maximumSize(1000)
                                                                                .build(CacheLoader.from(Utils::parsePermission));

    private static List<String> parsePermission(String str) {
        return Arrays.asList(str.split(";"));
    }

    public static List<Entity> getNearbyEntities(Power power, Location l, Player player, double radius, double dx, double dy, double dz) {
        List<Entity> entities = new ArrayList<>();
        Collection<Entity> nearbyEntities = l.getWorld().getNearbyEntities(l, dx, dy, dz);
        if (!nearbyEntities.isEmpty()) {
            for (Entity e : nearbyEntities) {
                if (l.distance(e.getLocation()) <= radius) {
                    entities.add(e);
                }
            }
        }
        power.getItem().getPowers().stream().filter(pow -> pow instanceof Selector).forEach(
                selector -> {
                    if (power.getSelectors().contains(((Selector) selector).id())) {
                        ((Selector) selector).inPlaceFilter(player, entities);
                    }
                }
        );
        return entities;
    }

    /**
     * Get nearby entities.
     *
     * @param power  power
     * @param l      location
     * @param player player
     * @param radius radius
     * @return nearby entities
     */
    public static List<Entity> getNearbyEntities(Power power, Location l, Player player, double radius) {
        return getNearbyEntities(power, l, player, radius, radius, radius, radius);
    }

    /**
     * Get nearby living entities ordered by distance.
     *
     * @param power  power
     * @param l      location
     * @param player player
     * @param radius radius
     * @param min    min radius
     * @return nearby living entities ordered by distance
     */
    public static List<LivingEntity> getNearestLivingEntities(Power power, Location l, Player player, double radius, double min) {
        final List<Map.Entry<LivingEntity, Double>> entities = new ArrayList<>();
        for (Entity e : getNearbyEntities(power, l, player, radius)) {
            if (e instanceof LivingEntity && !player.equals(e)) {
                double d = l.distance(e.getLocation());
                if (d <= radius && d >= min) {
                    entities.add(new AbstractMap.SimpleImmutableEntry<>((LivingEntity) e, d));
                }
            }
        }
        List<LivingEntity> entity = new ArrayList<>();
        entities.sort(Comparator.comparing(Map.Entry::getValue));
        entities.forEach((k) -> entity.add(k.getKey()));
        return entity;
    }

    /**
     * Gets entities in cone.
     *
     * @param entities  List of nearby entities
     * @param startPos  starting position
     * @param degrees   angle of cone
     * @param direction direction of the cone
     * @return All entities inside the cone
     */
    public static List<LivingEntity> getLivingEntitiesInCone(List<LivingEntity> entities, org.bukkit.util.Vector startPos, double degrees, org.bukkit.util.Vector direction) {
        List<LivingEntity> newEntities = new LinkedList<>();
        float relativeAngle = 0;
        float minAngle = 180;
        for (LivingEntity e : entities) {
            org.bukkit.util.Vector relativePosition = e.getEyeLocation().toVector();
            relativePosition.subtract(startPos);
            relativeAngle = getAngleBetweenVectors(direction, relativePosition);
            if (relativeAngle > degrees) continue;
            if (relativeAngle < minAngle) {
                minAngle = relativeAngle;
                newEntities.add(0, e);
            } else {
                newEntities.add(e);
            }
        }
        return newEntities;
    }

    public static List<LivingEntity> getLivingEntitiesInConeSorted(List<LivingEntity> entities, org.bukkit.util.Vector startPos, double degrees, org.bukkit.util.Vector direction) {
        Set<AngledEntity> newEntities = new TreeSet<>();
        float relativeAngle = 0;
        for (LivingEntity e : entities) {
            org.bukkit.util.Vector relativePosition = e.getEyeLocation().toVector();
            relativePosition.subtract(startPos);
            relativeAngle = getAngleBetweenVectors(direction, relativePosition);
            AngledEntity angledEntity = new AngledEntity(relativeAngle, e);
            if (relativeAngle > degrees) continue;
            newEntities.add(angledEntity);
        }
        return newEntities.stream().map(AngledEntity::getEntity).collect(Collectors.toList());
    }

    private static class AngledEntity implements Comparable<Double>{
        double angle;
        LivingEntity entity;

        public AngledEntity(double angle, LivingEntity entity){
            this.angle = angle;
            this.entity = entity;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public double getAngle() {
            return angle;
        }

        @Override
        public int compareTo(Double o) {
            return ((Double)angle).compareTo(o);
        }
    }


    /**
     * Gets angle between vectors.
     *
     * @param v1 the v 1
     * @param v2 the v 2
     * @return the angle between vectors
     */
    public static float getAngleBetweenVectors(org.bukkit.util.Vector v1, org.bukkit.util.Vector v2) {
        return Math.abs((float) Math.toDegrees(v1.angle(v2)));
    }

    /**
     * Check cooldown boolean.
     *
     * @param power     Power
     * @param player    the player
     * @param cdTicks   the cd ticks
     * @param showWarn  whether to show warning to player
     * @param showPower whether to show power name in warning
     * @return the boolean
     */
    public static boolean checkCooldown(Power power, Player player, long cdTicks, boolean showWarn, boolean showPower) {
        return checkAndSetCooldown(power, player, cdTicks, showWarn, showPower, "cooldown." + power.getItem().getUid() + "." + power.getNamespacedKey().toString());
    }

    public static boolean checkAndSetCooldown(Power power, Player player, long cooldownTime, boolean showWarn, boolean showPower, String key) {
        long cooldown;
        Long value = (Long) Context.instance().get(player.getUniqueId(), key);
        long nowTick = System.currentTimeMillis() / 50;
        if (value == null) {
            cooldown = nowTick;
        } else {
            cooldown = value;
        }
        if (cooldown <= nowTick) {
            long cd = nowTick + cooldownTime;
            Context.instance().put(player.getUniqueId(), key, cd, cd);
            return true;
        } else {
            if (showWarn) {
                I18n i18n = I18n.getInstance(player.getLocale());
                if (showPower || (!Strings.isNullOrEmpty(power.getLocalizedDisplayName()) && !power.getLocalizedDisplayName().equals(power.getLocalizedName(RPGItems.plugin.cfg.language)))) {
                    player.sendMessage(I18n.formatDefault("message.cooldown.power", ((double) (cooldown - nowTick)) / 20d, power.getLocalizedDisplayName()));
                } else {
                    player.sendMessage(I18n.formatDefault("message.cooldown.general", ((double) (cooldown - nowTick)) / 20d));
                }
            }
            return false;
        }
    }

    public static void attachPermission(Player player, String permissions) {
        if (permissions.length() != 0 && !permissions.equals("*")) {
            List<String> permissionList = permissionCache.getUnchecked(permissions);
            for (String permission : permissionList) {
                if (player.hasPermission(permission)) {
                    return;
                }
                PermissionAttachment attachment = player.addAttachment(RPGItems.plugin, 1);
                String[] perms = permission.split("\\.");
                StringBuilder p = new StringBuilder();
                for (String perm : perms) {
                    p.append(perm);
                    attachment.setPermission(p.toString(), true);
                    p.append('.');
                }
            }
        }
    }

    // TODO
    @SuppressWarnings("unchecked")
    public static void saveProperty(PropertyHolder p, ConfigurationSection section, String property, Field field) throws IllegalAccessException {
        Serializer getter = field.getAnnotation(Serializer.class);
        Object val = field.get(p);
        if (val == null) return;
        if (getter != null) {
            section.set(property, Getter.from(p, getter.value()).get(val));
        } else {
            if (Collection.class.isAssignableFrom(field.getType())) {
                Collection c = (Collection) val;
                if (c.isEmpty()) return;
                if (Set.class.isAssignableFrom(field.getType())) {
                    section.set(property, c.stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                } else {
                    section.set(property, c.stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            } else if (field.getType() == Enchantment.class) {
                section.set(property, ((Enchantment) val).getKey().toString());
            } else {
                val = field.getType().isEnum() ? ((Enum<?>) val).name() : val;
                section.set(property, val);
            }
        }
    }

    public static String getProperty(PropertyHolder p, String property, Field field) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            saveProperty(p, configuration, property, field);
        } catch (IllegalAccessException e) {
            RPGItems.plugin.getLogger().log(Level.WARNING, "Error getting property " + property + " from " + field + " in " + p, e);
            return null;
        }
        return configuration.getString(property);
    }

    public static Vector distance(BoundingBox bb, Vector vec) {
        double x = Math.max(0, Math.max(bb.getMinX() - vec.getX(), vec.getX() - bb.getMaxX()));
        double y = Math.max(0, Math.max(bb.getMinY() - vec.getY(), vec.getY() - bb.getMaxY()));
        double z = Math.max(0, Math.max(bb.getMinZ() - vec.getZ(), vec.getZ() - bb.getMaxZ()));
        return new Vector(x, y, z);
    }

    public static Vector hitPoint(BoundingBox bb, Vector hitNormal) {
        if (hitNormal.getX() > 0) {
            return new Vector(bb.getMinX(), bb.getCenterY(), bb.getCenterZ());
        }
        if (hitNormal.getX() < 0) {
            return new Vector(bb.getMaxX(), bb.getCenterY(), bb.getCenterZ());
        }
        if (hitNormal.getY() > 0) {
            return new Vector(bb.getCenterX(), bb.getMinY(), bb.getCenterZ());
        }
        if (hitNormal.getY() < 0) {
            return new Vector(bb.getCenterX(), bb.getMaxY(), bb.getCenterZ());
        }
        if (hitNormal.getZ() > 0) {
            return new Vector(bb.getCenterX(), bb.getCenterY(), bb.getMinZ());
        }
        if (hitNormal.getZ() < 0) {
            return new Vector(bb.getCenterX(), bb.getCenterY(), bb.getMaxZ());
        }
        throw new IllegalArgumentException("hitNormal: " + hitNormal.toString());
    }

    // Sweep a in the direction of v against b, returns non null & info if there was a hit
    // ===================================================================
    public static Pair<Vector, Vector> sweep(BoundingBox a, BoundingBox b, Vector vel) {
        double outTime = 1.0;
        // Return early if a & b are already overlapping
        if (a.overlaps(b)) return Pair.of(new Vector(), null);

        // Treat b as stationary, so invert v to get relative velocity
        Vector v = vel.clone().multiply(-1);

        double hitTime = 0.0;
        Vector overlapTime = new Vector();

        // X axis overlap
        if (v.getX() < 0) {
            if (b.getMax().getX() < a.getMin().getX()) return null;
            if (b.getMax().getX() > a.getMin().getX())
                outTime = Math.min((a.getMin().getX() - b.getMax().getX()) / v.getX(), outTime);

            if (a.getMax().getX() < b.getMin().getX()) {
                overlapTime.setX((a.getMax().getX() - b.getMin().getX()) / v.getX());
                hitTime = Math.max(overlapTime.getX(), hitTime);
            }
        } else if (v.getX() > 0) {
            if (b.getMin().getX() > a.getMax().getX()) return null;
            if (a.getMax().getX() > b.getMin().getX())
                outTime = Math.min((a.getMax().getX() - b.getMin().getX()) / v.getX(), outTime);

            if (b.getMax().getX() < a.getMin().getX()) {
                overlapTime.setX((a.getMin().getX() - b.getMax().getX()) / v.getX());
                hitTime = Math.max(overlapTime.getX(), hitTime);
            }
        }

        if (hitTime > outTime) return null;

        //=================================

        // Y axis overlap
        if (v.getY() < 0) {
            if (b.getMax().getY() < a.getMin().getY()) return null;
            if (b.getMax().getY() > a.getMin().getY())
                outTime = Math.min((a.getMin().getY() - b.getMax().getY()) / v.getY(), outTime);

            if (a.getMax().getY() < b.getMin().getY()) {
                overlapTime.setY((a.getMax().getY() - b.getMin().getY()) / v.getY());
                hitTime = Math.max(overlapTime.getY(), hitTime);
            }
        } else if (v.getY() > 0) {
            if (b.getMin().getY() > a.getMax().getY()) return null;
            if (a.getMax().getY() > b.getMin().getY())
                outTime = Math.min((a.getMax().getY() - b.getMin().getY()) / v.getY(), outTime);

            if (b.getMax().getY() < a.getMin().getY()) {
                overlapTime.setY((a.getMin().getY() - b.getMax().getY()) / v.getY());
                hitTime = Math.max(overlapTime.getY(), hitTime);
            }
        }

        if (hitTime > outTime) return null;

        //=================================

        // Z axis overlap
        if (v.getZ() < 0) {
            if (b.getMax().getZ() < a.getMin().getZ()) return null;
            if (b.getMax().getZ() > a.getMin().getZ())
                outTime = Math.min((a.getMin().getZ() - b.getMax().getZ()) / v.getZ(), outTime);

            if (a.getMax().getZ() < b.getMin().getZ()) {
                overlapTime.setZ((a.getMax().getZ() - b.getMin().getZ()) / v.getZ());
                hitTime = Math.max(overlapTime.getZ(), hitTime);
            }
        } else if (v.getZ() > 0) {
            if (b.getMin().getZ() > a.getMax().getZ()) return null;
            if (a.getMax().getZ() > b.getMin().getZ())
                outTime = Math.min((a.getMax().getZ() - b.getMin().getZ()) / v.getZ(), outTime);

            if (b.getMax().getZ() < a.getMin().getZ()) {
                overlapTime.setZ((a.getMin().getZ() - b.getMax().getZ()) / v.getZ());
                hitTime = Math.max(overlapTime.getZ(), hitTime);
            }
        }

        if (hitTime > outTime) return null;
        Vector hitNormal;
        // Scale resulting velocity by normalized hit time
        Vector outVel = vel.clone().multiply(hitTime);

        // Hit normal is along axis with the highest overlap time
        if (overlapTime.getX() > overlapTime.getY()) {
            if (overlapTime.getZ() > overlapTime.getX()) {
                hitNormal = new Vector(0, 0, v.getZ());
            } else {
                hitNormal = new Vector(v.getX(), 0, 0);
            }
        } else {
            if (overlapTime.getZ() > overlapTime.getY()) {
                hitNormal = new Vector(0, 0, v.getZ());
            } else {
                hitNormal = new Vector(0, v.getY(), 0);
            }
        }

        return Pair.of(outVel, hitNormal);
    }

    @Deprecated
    public static List<String> wrapLines(String txt, int maxwidth) {
        List<String> words = new ArrayList<>();
        for (String word : txt.split(" ")) {
            if (word.length() > 0)
                words.add(word);
        }
        if (words.size() <= 0) return Collections.emptyList();

        for (String str : words) {
            int len = getStringWidth(ChatColor.stripColor(str));
            if (len > maxwidth) maxwidth = len;
        }

        List<String> ans = new ArrayList<>();
        int idx = 0, currlen = getStringWidth(ChatColor.stripColor(words.get(0)));
        ans.add(words.remove(0));
        while (words.size() > 0) {
            String tmp = words.remove(0);
            int word_len = getStringWidth(ChatColor.stripColor(tmp));
            if (currlen + 4 + word_len <= maxwidth) {
                currlen += 4 + word_len;
                ans.set(idx, ans.get(idx) + " " + tmp);
            } else {
                currlen = word_len;
                ans.add(tmp);
                idx++;
            }
        }
        for (int i = 1; i < ans.size(); i++) {
            ans.set(i, getLastFormat(ans.get(i - 1)) + ans.get(i));
        }
        return ans;
    }

    @Deprecated
    public static int getStringWidth(String str) {
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            width += Font.widths[c] + 1;
        }
        return width;
    }

    @Deprecated
    private static String getLastFormat(String str) {
        String format = null;
        int length = str.length();

        for (int index = length - 2; index > -1; index--) {
            char chr = str.charAt(index);
            if (chr == ChatColor.COLOR_CHAR) {
                char c = str.charAt(index + 1);
                ChatColor style = ChatColor.getByChar(c);
                if (style == null) continue;
                if (style.isColor()) return style.toString() + (format == null ? "" : format);
                if (style.isFormat() && format == null) format = style.toString();
            }
        }

        return (format == null ? "" : format);
    }

    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9/._-]+");

    @SuppressWarnings({"unchecked", "deprecation"})
    public static void setPowerPropertyUnchecked(CommandSender sender, PropertyHolder power, Field field, String value) {
        String locale = RPGItems.plugin.cfg.language;
        if (sender instanceof Player) {
            locale = ((Player) sender).getLocale();
        }
        I18n i18n = I18n.getInstance(locale);
        try {
            if (value.equals("null")) {
                field.set(power, null);
                return;
            }
            Deserializer st = field.getAnnotation(Deserializer.class);
            field.setAccessible(true);
            if (st != null) {
                try {
                    Optional<Object> v = Setter.from(power, st.value()).set(value);
                    if (!v.isPresent()) return;
                    field.set(power, v.get());
                } catch (IllegalArgumentException e) {
                    new Message(I18n.formatDefault(st.message(), value)).send(sender);
                }
            } else {
                if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                    try {
                        field.set(power, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        throw new AdminCommands.CommandException("internal.error.bad_int", value);
                    }
                } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                    try {
                        field.set(power, Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        throw new AdminCommands.CommandException("internal.error.bad_int", value);
                    }
                } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                    try {
                        field.set(power, Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        throw new AdminCommands.CommandException("internal.error.bad_double", value);
                    }
                } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                    try {
                        field.set(power, Double.parseDouble(value));
                    } catch (NumberFormatException e) {
                        throw new AdminCommands.CommandException("internal.error.bad_double", value);
                    }
                } else if (field.getType().equals(String.class)) {
                    field.set(power, value);
                } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                        field.set(power, Boolean.valueOf(value));
                    } else {
                        throw new AdminCommands.CommandException("message.error.invalid_option", value, field.getName(), "true, false");
                    }
                } else if (field.getType().isEnum()) {
                    try {
                        field.set(power, Enum.valueOf((Class<Enum>) field.getType(), value));
                    } catch (IllegalArgumentException e) {
                        throw new AdminCommands.CommandException("internal.error.bad_enum", field.getName(), Stream.of(field.getType().getEnumConstants()).map(Object::toString).collect(Collectors.joining(", ")));
                    }
                } else if (Collection.class.isAssignableFrom(field.getType())) {
                    ParameterizedType listType = (ParameterizedType) field.getGenericType();
                    Class<?> listArg = (Class<?>) listType.getActualTypeArguments()[0];
                    String[] valueStrs = value.split(",");
                    Stream<String> values = Arrays.stream(valueStrs).filter(s -> !s.isEmpty()).map(String::trim);
                    if (field.getType().equals(List.class)) {
                        if (listArg.isEnum()) {
                            Class<? extends Enum> enumClass = (Class<? extends Enum>) listArg;
                            Stream<Enum> enumStream = values.map(v -> Enum.valueOf(enumClass, v));
                            List<Enum> list = enumStream.collect(Collectors.toList());
                            field.set(power, list);
                        } else if (listArg.equals(String.class)) {
                            List<String> list = values.collect(Collectors.toList());
                            field.set(power, list);
                        } else if (listArg.equals(Integer.class)) {
                            List<Integer> list = values.map(Integer::parseInt).collect(Collectors.toList());
                            field.set(power, list);
                        } else if (listArg.equals(Double.class)) {
                            List<Double> list = values.map(Double::parseDouble).collect(Collectors.toList());
                            field.set(power, list);
                        } else {
                            throw new AdminCommands.CommandException("internal.error.command_exception");
                        }
                    } else {
                        if (listArg.isEnum()) {
                            Class<? extends Enum> enumClass = (Class<? extends Enum>) listArg;
                            Stream<Enum> enumStream = values.map(v -> Enum.valueOf(enumClass, v));
                            Set<Enum> set = enumStream.collect(Collectors.toSet());
                            field.set(power, set);
                        } else if (listArg.equals(String.class)) {
                            Set<String> set = values.collect(Collectors.toSet());
                            field.set(power, set);
                        } else if (listArg.equals(Trigger.class)) {
                            Set<String> ignored = new LinkedHashSet<>();
                            Set<Trigger> set = Trigger.getValid(values.collect(Collectors.toList()), ignored);
                            if (!ignored.isEmpty()) {
                                new Message(I18n.formatDefault("message.power.ignored_trigger", String.join(", ", ignored), power.getName(), power.getItem().getName())).send(sender);
                            }
                            field.set(power, set);
                        } else if (listArg.equals(Integer.class)) {
                            Set<Integer> list = values.map(Integer::parseInt).collect(Collectors.toSet());
                            field.set(power, list);
                        } else if (listArg.equals(Double.class)) {
                            Set<Double> list = values.map(Double::parseDouble).collect(Collectors.toSet());
                            field.set(power, list);
                        } else {
                            throw new AdminCommands.CommandException("internal.error.command_exception");
                        }
                    }
                } else if (field.getType() == ItemStack.class) {
                    Material m = MaterialUtils.getMaterial(value, sender);
                    ItemStack item;
                    if (sender instanceof Player && value.equalsIgnoreCase("HAND")) {
                        ItemStack hand = ((Player) sender).getInventory().getItemInMainHand();
                        if (hand == null || hand.getType() == Material.AIR) {
                            throw new AdminCommands.CommandException("message.error.iteminhand");
                        }
                        item = hand.clone();
                        item.setAmount(1);
                    } else if (m == null || m == Material.AIR || !m.isItem()) {
                        throw new AdminCommands.CommandException("message.error.material", value);
                    } else {
                        item = new ItemStack(m);
                    }
                    field.set(power, item.clone());
                } else if (field.getType() == Enchantment.class) {
                    Enchantment enchantment;
                    if (VALID_KEY.matcher(value).matches()) {
                        enchantment = Enchantment.getByKey(NamespacedKey.minecraft(value));
                    } else if (value.contains(":")) {
                        if (value.startsWith("minecraft:")) {
                            enchantment = Enchantment.getByKey(NamespacedKey.minecraft(value.split(":", 2)[1]));
                        } else {
                            enchantment = Enchantment.getByKey(new NamespacedKey(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(value.split(":", 2)[0])), value.split(":", 2)[1]));
                        }
                    } else {
                        enchantment = Enchantment.getByName(value);
                    }
                    if (enchantment == null) {
                        enchantment = Arrays.stream(Enchantment.class.getDeclaredFields()).parallel().filter(f -> Modifier.isStatic(f.getModifiers())).filter(f -> f.getName().equals(value)).findAny().map(f -> {
                            try {
                                return (Enchantment) f.get(null);
                            } catch (IllegalAccessException e) {
                                throw new AdminCommands.CommandException("message.error.invalid_enchant", e);
                            }
                        }).orElse(null);
                    }
                    field.set(power, enchantment);
                } else {
                    throw new AdminCommands.CommandException("message.error.invalid_command_arg", power.getName(), field.getName());
                }
            }
        } catch (IllegalAccessException e) {
            throw new AdminCommands.CommandException("internal.error.command_exception", e);
        }
    }

    public static void setPowerProperty(CommandSender sender, PropertyHolder power, Field field, String value) throws
            IllegalAccessException {
        Class<? extends PropertyHolder> cls = power.getClass();
        BooleanChoice bc = field.getAnnotation(BooleanChoice.class);
        if (bc != null) {
            String trueChoice = bc.trueChoice();
            String falseChoice = bc.falseChoice();
            if (value.equalsIgnoreCase(trueChoice) || value.equalsIgnoreCase(falseChoice)) {
                field.set(power, value.equalsIgnoreCase(trueChoice));
            } else {
                throw new AdminCommands.CommandException("message.error.invalid_option", value, field.getName(), falseChoice + ", " + trueChoice);//TODO
            }
            return;
        }
        AcceptedValue as = field.getAnnotation(AcceptedValue.class);
        if (as != null) {
            List<String> acc = PowerManager.getAcceptedValue(cls, as);
            if (!Collection.class.isAssignableFrom(field.getType())) {
                if (!acc.contains(value))
                    throw new AdminCommands.CommandException("message.error.invalid_option", value, field.getName(), String.join(", ", acc));
            } else {
                String[] valueStrs = value.split(",");
                List<String> values = Arrays.stream(valueStrs).filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.toList());
                if (values.stream().filter(s -> !s.isEmpty()).anyMatch(v -> !acc.contains(v))) {
                    throw new AdminCommands.CommandException("message.error.invalid_option", value, field.getName(), String.join(", ", acc));
                }
            }
        }
        setPowerPropertyUnchecked(sender, power, field, value);
    }

    public static byte[] decodeUUID(UUID complex) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(complex.getMostSignificantBits());
        bb.putLong(complex.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID encodeUUID(byte[] primitive) {
        ByteBuffer bb = ByteBuffer.wrap(primitive);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    public static void rethrow(Exception e) {
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new RuntimeException(e);
        }
    }

    public static Float maxWithCancel(Float a, Float b) {
        if (a == null) {
            return b;
        }
        if (a == -1 || b == -1) return -1.0f;
        return Math.max(a, b);
    }

    public static Double maxWithCancel(Double a, Double b) {
        if (a == null) {
            return b;
        }
        if (a == -1 || b == -1) return -1.0;
        return Math.max(a, b);
    }

    public static Double minWithCancel(Double a, Double b) {
        if (a == null) {
            return b;
        }
        if (a == -1 || b == -1) return -1.0;
        return Math.min(a, b);
    }

    public static Expression.LazyNumber lazyNumber(Supplier<Double> f) {
        return new Expression.LazyNumber() {
            @Override
            public BigDecimal eval() {
                return BigDecimal.valueOf(f.get());
            }

            @Override
            public String getString() {
                return null;
            }
        };
    }

    public static LazyFunction scoreBoard(Player player) {
        return new LazyFunction() {
            @Override
            public String getName() {
                return "playerScoreBoard";
            }

            @Override
            public int getNumParams() {
                return 2;
            }

            @Override
            public boolean numParamsVaries() {
                return false;
            }

            @Override
            public boolean isBooleanFunction() {
                return false;
            }

            @Override
            public Expression.LazyNumber lazyEval(List<Expression.LazyNumber> lazyParams) {
                Objective objective = player.getScoreboard().getObjective(lazyParams.get(0).getString());
                if (objective == null) {
                    return lazyParams.get(1);
                }
                return lazyNumber(() -> (double) objective.getScore(player.getName()).getScore());
            }
        };
    }

    public static LazyFunction context(Player player) {
        return new LazyFunction() {
            @Override
            public String getName() {
                return "playerContext";
            }

            @Override
            public int getNumParams() {
                return 2;
            }

            @Override
            public boolean numParamsVaries() {
                return false;
            }

            @Override
            public boolean isBooleanFunction() {
                return false;
            }

            @Override
            public Expression.LazyNumber lazyEval(List<Expression.LazyNumber> lazyParams) {
                Object obj = Context.instance().get(player.getUniqueId(), lazyParams.get(0).getString());
                if (obj == null) {
                    return lazyParams.get(1);
                }
                return new Expression.LazyNumber() {
                    @Override
                    public BigDecimal eval() {
                        if (obj instanceof Number) {
                            return BigDecimal.valueOf(((Number) obj).doubleValue());
                        }
                        return null;
                    }

                    @Override
                    public String getString() {
                        return obj.toString();
                    }
                };
            }
        };
    }

    public static LazyFunction now() {
        return new LazyFunction() {
            @Override
            public String getName() {
                return "now";
            }

            @Override
            public int getNumParams() {
                return 0;
            }

            @Override
            public boolean numParamsVaries() {
                return false;
            }

            @Override
            public boolean isBooleanFunction() {
                return false;
            }

            @Override
            public Expression.LazyNumber lazyEval(List<Expression.LazyNumber> lazyParams) {
                return new Expression.LazyNumber() {
                    @Override
                    public BigDecimal eval() {
                        return BigDecimal.valueOf(System.currentTimeMillis());
                    }

                    @Override
                    public String getString() {
                        return null;
                    }
                };
            }
        };
    }
}
