package net.chrislehmann.squeezedroid.activity;

import net.chrislehmann.squeezedroid.listadapter.ArtistExpandableListAdapter;
import net.chrislehmann.squeezedroid.model.Artist;
import net.chrislehmann.squeezedroid.model.Genre;
import net.chrislehmann.squeezedroid.model.Item;
import net.chrislehmann.squeezedroid.service.SqueezeService;
import android.app.Activity;
import android.app.ExpandableListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ExpandableListView;
import android.widget.Toast;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

public class BrowseArtistsActivity extends ExpandableListActivity {

	private Activity context = this;

	public static final int DIALOG_CHOOSE_ACTION = 901;

	private static final int CONTEXTMENU_ADD_ITEM = 801;

	private static final int CONTEXTMENU_PLAY_ITEM = 802;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SqueezeDroidApplication application = (SqueezeDroidApplication) getApplication();

		Item parentItem = getParentItem();
		setListAdapter(new ArtistExpandableListAdapter(application.getService(), this, parentItem));
		getExpandableListView().setFastScrollEnabled(true);
		getExpandableListView().setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				menu.add(Menu.NONE, CONTEXTMENU_ADD_ITEM, 0, "Add To Playlist");
				menu.add(Menu.NONE, CONTEXTMENU_PLAY_ITEM, 1, "Play Now");
			}
		});

	};

	private Item getParentItem() {
		Uri data = getIntent().getData();
		Item item = null;
		if (data.getPathSegments().size() >= 2) {
			String type = data.getPathSegments().get(0);
			String id = data.getPathSegments().get(1);
			if ("genre".equalsIgnoreCase(type)) {
				item = new Genre();
			}
			item.setId(id);
		}
		return item;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		boolean handled = true;
		ExpandableListContextMenuInfo menuInfo = (ExpandableListContextMenuInfo) item.getMenuInfo();
		int group = ExpandableListView.getPackedPositionGroup(menuInfo.packedPosition);
		int child = ExpandableListView.getPackedPositionChild(menuInfo.packedPosition);

		SqueezeService service = ActivityUtils.getService(context);
		if (service != null) {

			Item selectedItem = null;
			if (child >= 0) {
				selectedItem = (Item) getExpandableListAdapter().getChild(group, child);
			} else {
				selectedItem = (Item) getExpandableListAdapter().getGroup(group);
			}

			switch (item.getItemId()) {
			case CONTEXTMENU_ADD_ITEM:
				service.addItem(ActivityUtils.getSqueezeDroidApplication(context).getSelectedPlayer(), selectedItem);
				Toast.makeText(context, selectedItem.getName() + " added to playlist.", Toast.LENGTH_SHORT);
				break;
			case CONTEXTMENU_PLAY_ITEM:
				service.playItem(ActivityUtils.getSqueezeDroidApplication(context).getSelectedPlayer(), selectedItem);
				Toast.makeText(context, "Now playing " + selectedItem.getName(), Toast.LENGTH_SHORT);
				break;
			default:
				handled = false;
			}

		}

		return handled;
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		Item item = (Item) getExpandableListAdapter().getChild(groupPosition, childPosition);
		Intent i = new Intent();
		i.setAction("net.chrislehmann.squeezedroid.action.BrowseSong");
		i.setData(Uri.parse("squeeze:///album/" + item.getId()));
		startActivity(i);
		return super.onChildClick(parent, v, groupPosition, childPosition, id);
	}

}