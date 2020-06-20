package net.runelite.client.plugins.itemuser;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.queries.GameObjectQuery;
import net.runelite.api.queries.InventoryWidgetItemQuery;
import net.runelite.api.util.Text;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.util.HotkeyListener;
import org.pf4j.Extension;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Extension
@PluginDescriptor(
	name = "Item User",
	description = "Automatically uses items on an object",
	tags = {"skilling", "item", "object", "user"},
	enabledByDefault = false,
	type = PluginType.SKILLING
)
public class ItemUserPlugin extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	ItemUserConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private MenuManager menuManager;

	private BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1);
	private ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 25, TimeUnit.SECONDS, queue,
		new ThreadPoolExecutor.DiscardPolicy());

	private GameObject object;
	private final List<WidgetItem> items = new ArrayList<>();
	private String item_name;
	private boolean iterating;
	private int iterTicks;

	private MenuEntry entry;

	private final HotkeyListener toggle = new HotkeyListener(() -> config.useItemsKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			List<WidgetItem> list = new InventoryWidgetItemQuery()
				.idEquals(config.itemId())
				.result(client)
				.list;

			items.addAll(list);
			object = findNearestGameObject(config.objectId());
			item_name = Text.standardize(itemManager.getItemDefinition(config.itemId()).getName());
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		this.keyManager.registerKeyListener(toggle);
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.keyManager.unregisterKeyListener(toggle);
	}

	@Subscribe
	private void onGameTick(final GameTick event)
	{
		if (items.isEmpty())
		{
			if (iterating)
			{
				iterTicks++;
				if (iterTicks > 10)
				{
					iterating = false;
				}
			}
			else
			{
				if (iterTicks > 0)
				{
					iterTicks = 0;
				}
			}
			return;
		}
		executor.submit(() -> {
			useItems();
			System.out.println("Clearing items");
			items.clear();
		});
	}

	private void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	private void useItems()
	{
		iterating = true;

		if (items == null || items.isEmpty())
		{
			return;
		}

		if (item_name.isBlank() || item_name.isEmpty())
		{
			return;
		}

		if (object == null)
		{
			return;
		}

		System.out.println("Using items");
		for (WidgetItem item : items)
		{
			entry = new MenuEntry("Use", "<col=ff9040>" + itemManager.getItemDefinition(item.getId()).getName(), item.getId(), MenuOpcode.ITEM_USE.getId(), item.getIndex(), 9764864, false);
			click();
			sleep(config.clickDelay());

			entry = new MenuEntry("Use", "<col=ff9040>" + itemManager.getItemDefinition(item.getId()).getName() + "<col=ffffff> -> <col=ffff>" + client.getObjectDefinition(object.getId()).getName(), object.getId(), MenuOpcode.ITEM_USE_ON_GAME_OBJECT.getId(), object.getSceneMinLocation().getX(), object.getSceneMinLocation().getY(), false);
			click();
			sleep(config.clickDelay());
		}
	}

	@Nullable
	public GameObject findNearestGameObject(int... ids)
	{
		assert client.isClientThread();

		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return new GameObjectQuery()
			.idEquals(ids)
			.result(client)
			.nearestTo(client.getLocalPlayer());
	}

	@Provides
	ItemUserConfig provideConfig(final ConfigManager configManager)
	{
		return configManager.getConfig(ItemUserConfig.class);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (entry != null)
		{
			event.setMenuEntry(entry);
		}

		entry = null;
	}

	public void click()
	{
		Point pos = client.getMouseCanvasPosition();

		if (client.isStretchedEnabled())
		{
			final Dimension stretched = client.getStretchedDimensions();
			final Dimension real = client.getRealDimensions();
			final double width = (stretched.width / real.getWidth());
			final double height = (stretched.height / real.getHeight());
			final Point point = new Point((int) (pos.getX() * width), (int) (pos.getY() * height));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 501, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 502, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 500, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			return;
		}

		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 501, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 502, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 500, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
	}
}
