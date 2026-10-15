package org.alvindimas05.lagassist;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alvindimas05.lagassist.packets.ServerPackage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.json.simple.JSONObject;
import org.bukkit.inventory.Inventory;

import org.alvindimas05.lagassist.utils.VersionMgr;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;

public class Reflection {

	private static Map<Class<?>, Field[]> cached = new HashMap<Class<?>, Field[]>();
	private static String version = ServerPackage.getServerVersion();

	public static enum Classes {

		CraftWorld(), CraftBlock(), CraftPlayer(), Material(), MapMeta(), WorldServer(), ChunkProviderServer(),
		PacketPlayOutTitle(), IChatBaseComponent(), World(), MinecraftServer(), Attribute();

		private Class<?> type;

		public Class<?> getType() {
			return type;
		}
	}

	public static enum Methods {

		setMapId(), getMapId(), getPlayerHandle(), getBlockType(), getChunkProviderServer(), chunkExists(),
		getIChatBaseComponent(), setViewDistance(), getServer();

		private Method mthd;

		public Method getMethod() {
			return mthd;
		}
	}

	public static void Enabler() {
		// PUTTING CLASSES IN ENUM.
		// NOTE: on modern Paper (1.20.5+) there is no versioned CraftBukkit package and NMS is
		// unobfuscated, so many of these lookups legitimately return null. Everything that
		// uses them must be null-safe (getClass/getMethod already return null on failure).
		Classes.CraftWorld.type = getClass("{cb}.CraftWorld");
		Classes.World.type = getClass("{b}.World");
		Classes.CraftBlock.type = getClass("{cb}.block.CraftBlock");
		Classes.CraftPlayer.type = getClass("{cb}.entity.CraftPlayer");
		Classes.Material.type = getClass("{b}.Material");
		Classes.MapMeta.type = getClass("{b}.inventory.meta.MapMeta");
		Classes.WorldServer.type = getClass(VersionMgr.isV_17Plus() ? "{nms}.level.WorldServer" : "{nms}.WorldServer");
		Classes.MinecraftServer.type = getClass("{nms}.MinecraftServer");
		Classes.ChunkProviderServer.type = getClass(VersionMgr.isV_17Plus() ? "{nms}.level.ChunkProviderServer" : "{nms}.ChunkProviderServer");
		Classes.IChatBaseComponent.type = getClass(VersionMgr.isV_17Plus() ? "{nm}.network.chat.IChatBaseComponent" : "{nms}.IChatBaseComponent");

		if(!VersionMgr.isV1_8()){
			Classes.Attribute.type = getClass("{b}.attribute.Attribute");
		}
		// PUTTING METHODS IN ENUM.
		Methods.setMapId.mthd = getMethod(Classes.MapMeta.getType(), "setMapId", int.class);
		Methods.getMapId.mthd = getMethod(Classes.MapMeta.getType(), "getMapId");
		Methods.getPlayerHandle.mthd = getMethod(Classes.CraftPlayer.getType(), "getHandle");
		Methods.getBlockType.mthd = getMethod(Classes.CraftBlock.getType(), "getType");
		Methods.getChunkProviderServer.mthd = getMethod(Classes.WorldServer.getType(), "getChunkProviderServer");
		Methods.getIChatBaseComponent.mthd = getMethod(Classes.IChatBaseComponent.getType(), "a", String.class);
		Methods.chunkExists.mthd = getMethod(Classes.ChunkProviderServer.getType(), VersionMgr.ChunkExistsName(),
				int.class, int.class);
		Methods.setViewDistance.mthd = getMethod(Classes.World.getType(), "setViewDistance", int.class);
		// No longer needed: TPS is read through the Paper API (see getTPS).
		Methods.getServer.mthd = getMethod(Classes.MinecraftServer.getType(), "getServer");
	}

	// InventoryView is an interface in newer versions; calling through the interface class
	// avoids IllegalAccessException on the non-public CraftBukkit implementation.
	private static Object callView(InventoryEvent event, String name) {
		try {
			Object view = event.getView();
			Method m = InventoryView.class.getMethod(name);
			return m.invoke(view);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Inventory getTopInventory(InventoryEvent event) {
		return (Inventory) callView(event, "getTopInventory");
	}

	public static Inventory getBottomInventory(InventoryEvent event) {
		return (Inventory) callView(event, "getBottomInventory");
	}

	public static String getInventoryViewTitle(InventoryEvent event){
		return (String) callView(event, "getTitle");
	}

	@SuppressWarnings("deprecation")
	public static void sendAction(Player player, String s) {
		if(VersionMgr.isV1_8()){
			try {
				Object chat = Reflection.getClass("{nmsv}.ChatComponentText")
						.getConstructor(String.class).newInstance(s);
				Object packet = getClass("{nmsv}.PacketPlayOutChat")
						.getConstructor(
								getClass("{nmsv}.IChatBaseComponent"), byte.class
						).newInstance(chat, (byte) 2);
				sendPlayerPacket(player, packet);
			} catch (Exception e){
				e.printStackTrace();
			}
		} else {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(s).create());
		}
	}

	public static MapView getMapView(int i) {
		Class<?> buk = Bukkit.class;

		try {
			Method getMap = buk.getDeclaredMethod("getMap", VersionMgr.isNewMaterials() ? int.class : short.class);
			return (MapView) (VersionMgr.isNewMaterials() ? getMap.invoke(null, i) : getMap.invoke(null, (short) i));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	/**
	 * Returns the server TPS using the Paper API.
	 * @param number 0 = last 1 minute, 1 = last 5 minutes, 2 = last 15 minutes
	 * @return the TPS, or -1 if unavailable
	 */
	public static double getTPS(int number) {
		try {
			double[] tps = Bukkit.getTPS();
			if (tps == null || tps.length == 0) {
				return -1;
			}
			if (number < 0) number = 0;
			if (number >= tps.length) number = tps.length - 1;
			return tps[number];
		} catch (Throwable t) {
			// Not Paper, or API unavailable. Don't spam the console from a repeating task.
			return -1;
		}
	}

	public static int getId(MapView view) {
		if (view == null) {
			return 0;
		}

		Class<?> mv = MapView.class;

		try {
			Method getMap = mv.getDeclaredMethod("getId");
			Object obj = getMap.invoke(view);
			if (obj instanceof Short) {
				return (short) obj;
			} else if (obj instanceof Integer) {
				return (int) obj;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	@SuppressWarnings("unchecked")
	static JSONObject convert(String text) {
		JSONObject json = new JSONObject();
		json.put("text", text);
		return json;
	}

	public static Class<?> getClass(String classname) {
		try {
			String path = classname.replace("{nms}", "net.minecraft.server" + (VersionMgr.isV_17Plus() ? "" : "." + version))
					.replace("{nmsv}", "net.minecraft.server." + version)
					.replace("{nm}", "net.minecraft" + (VersionMgr.isV_17Plus() ? "" : "." + version))
					.replace("{cb}", "org.bukkit.craftbukkit." + version)
					.replace("{b}", "org.bukkit");
			return Class.forName(path);
		} catch (Throwable e) {
			return null;
		}
	}

	public static Object getCraftWorld(World w) {
		// On modern Paper the World instance already is the CraftWorld.
		Class<?> crwclass = Classes.CraftWorld.getType();
		if (crwclass == null || !crwclass.isInstance(w)) {
			return w;
		}
		return crwclass.cast(w);
	}

	public static Object getWorldServer(Object craftWorld) {
		try {
			return getFieldValue(craftWorld, "world");
		} catch (Exception e) {
			// Field/class layout differs on newer versions; callers must tolerate null.
		}
		return null;
	}

	public static Object getChunkProvider(Object worldServer) {
		try {
			Method m = Methods.getChunkProviderServer.getMethod();
			if (worldServer == null || m == null) {
				return null;
			}
			return runMethod(worldServer, m);
		} catch (Exception e) {
			// ignore, see isChunkExistent
		}
		return null;
	}

	public static boolean isChunkExistent(Object chunkProvider, int x, int z) {
		try {
			Method m = Methods.chunkExists.getMethod();
			if (chunkProvider == null || m == null) {
				return true;
			}
			return (boolean) runMethod(chunkProvider, m, x, z);
		} catch (Exception e) {
			// ignore
		}
		// NOTE: ASSUMING TRUE IN ORDER TO IGNORE IF EXCEPTION POPS UP.
		return true;
	}

	public static boolean isTile(Block b) {
		return !b.getState().getClass().getSimpleName().toLowerCase().contains("craftblockstate");
	}

	public static Entity getEntity(Location l) {
		try {

			Collection<Entity> ents = l.getWorld().getNearbyEntities(l, 1, 1, 1);
			for (Entity ent : ents) {
				return ent;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public static void setmapId(ItemStack s, int id) {
		MapMeta mapm = (MapMeta) s.getItemMeta();
		try {
			Method m = Methods.setMapId.getMethod();
			if (m != null) {
				// Old servers: MapMeta#setMapId(int) still exists.
				runMethod(mapm, m, id);
			} else {
				// Newer servers: attach the MapView instead.
				MapView view = getMapView(id);
				if (view != null) {
					mapm.setMapView(view);
				} else {
					Main.sendDebug("setmapId: no MapView found for id " + id, 1);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		s.setItemMeta(mapm);
	}

	public static int getMapId(ItemStack s) {
		MapMeta mapm = (MapMeta) s.getItemMeta();
		try {
			Method m = Methods.getMapId.getMethod();
			if (m != null) {
				return (int) runMethod(mapm, m);
			}
			// Newer servers: read the id from the MapView.
			if (mapm.hasMapView()) {
				return getId(mapm.getMapView());
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return 0;
	}

	public static Object getNmsPlayer(Player p) {
		if (p == null) {
			return null;
		}
		Method getHandle;
		try {
			getHandle = p.getClass().getMethod("getHandle");
			return getHandle.invoke(p);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Object getNmsScoreboard(Scoreboard s) throws Exception {
		Method getHandle = s.getClass().getMethod("getHandle");
		return getHandle.invoke(s);
	}

	public static String getObjectSerialized(Object obj) {
		YamlConfiguration conf = new YamlConfiguration();
		Class<?> cls = obj.getClass();
		String loc = cls.getSimpleName();

		createObjectSerialized(conf, loc, obj, 0);

		return conf.saveToString();
	}

	public static void createObjectSerialized(YamlConfiguration conf, String loc, Object obj, int recursiveness) {

		Class<?> cls = obj.getClass();

		if (!cached.containsKey(cls)) {
			cached.put(cls, cls.getDeclaredFields());
		}

		for (Field fl : cached.get(cls)) {
			fl.setAccessible(true);

			Object newobj = getFieldValue(fl, obj);

			if (newobj == null) {
				continue;
			}

			String name = fl.getName();
			String type = newobj.getClass().getSimpleName();

			String newloc = loc + "." + type + "." + name;

			if (isSimple(newobj) || recursiveness > 1) {
				conf.set(newloc, convertToString(newobj));
				continue;
			}

			createObjectSerialized(conf, newloc, newobj, recursiveness + 1);

		}
	}

	public static Object getFieldValue(Object instance, String fieldName) throws Exception {
		Field field = instance.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(instance);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFieldValue(Field field, Object obj) {
		try {
			return (T) field.get(obj);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Field getField(Class<?> clazz, String fieldName) throws Exception {
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field;
	}

	public static Method getMethod(Class<?> clazz, String methodName, Class<?>... resl) {
		if (clazz == null) {
			return null;
		}
		Method method;
		try {
			method = clazz.getDeclaredMethod(methodName, resl);
			method.setAccessible(true);
			return method;
		} catch (Exception e) {
			return null;
		}
	}

	public static Object runMethod(Object obj, Method m, Object... resl) throws Exception {
		return m.invoke(obj, resl);
	}

	public static Object runMethod(Object obj, String name, Object... resl) throws Exception {
		Class<?>[] classes = new Class<?>[resl.length];
		for (int i = 0; i < resl.length; i++) {
			classes[i] = resl[i].getClass();
		}
		return getMethod(obj.getClass(), name, classes).invoke(obj, resl);
	}

	public static void setValue(Object instance, String field, Object value) {
		try {
			Field f = instance.getClass().getDeclaredField(field);
			f.setAccessible(true);
			f.set(instance, value);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void sendAllPacket(Object packet) throws Exception {
		for (Player p : Bukkit.getOnlinePlayers()) {
			Object nmsPlayer = getNmsPlayer(p);
			Object connection = nmsPlayer.getClass().getField("playerConnection").get(nmsPlayer);
			connection.getClass().getMethod("sendPacket", getClass("{nms}.Packet")).invoke(connection, packet);
		}
	}

	public static int getPing(Player p) {
		try {
			// Paper/Spigot API, no reflection needed.
			return p.getPing();
		} catch (Throwable e) {
			return -1;
		}
	}

	public static void sendListPacket(List<String> players, Object packet) {
		try {
			for (String name : players) {
				Object nmsPlayer = getNmsPlayer(Bukkit.getPlayer(name));
				Object connection = nmsPlayer.getClass().getField("playerConnection").get(nmsPlayer);
				connection.getClass().getMethod("sendPacket", getClass("{nms}.Packet")).invoke(connection, packet);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void sendPlayerPacket(Player p, Object packet) throws Exception {
		Object nmsPlayer = getNmsPlayer(p);
		Object connection = nmsPlayer.getClass().getField("playerConnection").get(nmsPlayer);
		if(VersionMgr.isV1_8()){
			connection.getClass().getMethod("sendPacket", getClass("{nmsv}.Packet")).invoke(connection, packet);
		} else {
			connection.getClass().getMethod("sendPacket", getClass("{nms}.Packet")).invoke(connection, packet);
		}
	}

	public static PluginCommand getCommand(String name, Plugin plugin) {
		PluginCommand command = null;

		try {
			Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
			c.setAccessible(true);

			command = c.newInstance(name, plugin);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return command;
	}

	public static CommandMap getCommandMap() {
		return Bukkit.getCommandMap();
	}

	private static String convertToString(Object obj) {
		Class<?> cls = obj.getClass();

		if (cls.isArray()) {
			String stg = "";
			int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				stg += convertToString(Array.get(obj, i));
			}
			return stg;

		}

		return obj.toString();
	}

	private static boolean isSimple(Object obj) {
		Class<?> cls = obj.getClass();

		return cls.isPrimitive() || cls.isEnum()
				|| cls == Integer.class || cls == Boolean.class || cls == String.class
				|| cls == Character.class || cls == Byte.class || cls == Short.class
				|| cls == Float.class || cls == Double.class || cls == Long.class;
	}

	public static void setViewDistance(World w, int amount) {
		try {
			// Paper API: World#setViewDistance(int). Called directly, no reflection.
			w.setViewDistance(amount);
			Main.sendDebug("Succesfully set view distance at " + amount + " in world " + w.getName(), 1);
		} catch (Throwable e) {
			Main.sendDebug("Exception at setViewDistance (" + w.getName() + ", " + amount + ")", 1);
		}
	}
}
