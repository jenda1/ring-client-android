/*
 *  Copyright (C) 2004-2013 Savoir-Faire Linux Inc.
 *
 *  Author: Alexandre Savard <alexandre.savard@savoirfairelinux.com>
 *          Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  If you modify this program, or any covered work, by linking or
 *  combining it with the OpenSSL project's OpenSSL library (or a
 *  modified version of that library), containing parts covered by the
 *  terms of the OpenSSL or SSLeay licenses, Savoir-Faire Linux Inc.
 *  grants you additional permission to convey the resulting work.
 *  Corresponding Source for a non-source form of such a combination
 *  shall include the source code for the parts of OpenSSL used as well
 *  as that of the covered work.
 */
package org.sflphone.fragments;

import java.util.ArrayList;

import org.sflphone.R;
import org.sflphone.adapters.ContactsAdapter;
import org.sflphone.adapters.StarredContactsAdapter;
import org.sflphone.loaders.ContactsLoader;
import org.sflphone.loaders.LoaderConstants;
import org.sflphone.model.CallContact;
import org.sflphone.service.ISipService;
import org.sflphone.views.SwipeListViewTouchListener;
import org.sflphone.views.TACGridView;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

public class ContactListFragment extends Fragment implements OnQueryTextListener, LoaderManager.LoaderCallbacks<Bundle> {
    private static final String TAG = "ContactListFragment";
    ContactsAdapter mListAdapter;
    StarredContactsAdapter mGridAdapter;
    SearchView search;
    String mCurFilter;

    @Override
    public void onCreate(Bundle savedInBundle) {
        super.onCreate(savedInBundle);
        mListAdapter = new ContactsAdapter(this);
        mGridAdapter = new StarredContactsAdapter(getActivity());
    }

    public Callbacks mCallbacks = sDummyCallbacks;
    /**
     * A dummy implementation of the {@link Callbacks} interface that does nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onCallContact(CallContact c) {
        }

        @Override
        public void onTextContact(CallContact c) {
        }

        @Override
        public void onEditContact(CallContact c) {
        }

        @Override
        public ISipService getService() {
            Log.i(TAG, "Dummy");
            return null;
        }

        @Override
        public void onContactDragged() {
        }

        @Override
        public void openDrawer() {
        }
    };

    public interface Callbacks {
        void onCallContact(CallContact c);

        void onTextContact(CallContact c);

        public ISipService getService();

        void onContactDragged();

        void openDrawer();

        void onEditContact(CallContact item);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In order to onCreateOptionsMenu be called
        setHasOptionsMenu(true);
        getLoaderManager().initLoader(LoaderConstants.CONTACT_LOADER, null, this);

    }

    ListView list;
    private TACGridView grid;

    private OnItemLongClickListener mItemLongClickListener = new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> av, View view, int pos, long id) {
            DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view.findViewById(R.id.photo));
            view.startDrag(null, shadowBuilder, view, 0);
            // view.setVisibility(View.INVISIBLE);
            mCallbacks.onContactDragged();
            // ((SearchView) mHandle.findViewById(R.id.contact_search_text)).setIconified(true);
            return true;
        }

    };
    private SwipeListViewTouchListener mSwipeLvTouchListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflatedView = inflater.inflate(R.layout.frag_contact_list, container, false);
        list = (ListView) inflatedView.findViewById(R.id.contacts_list);

       

        View header = inflater.inflate(R.layout.frag_contact_list_header, null);
        list.addHeaderView(header, null, false);
        list.setAdapter(mListAdapter);
        list.setEmptyView(inflatedView.findViewById(android.R.id.empty));

        search = (SearchView) inflatedView.findViewById(R.id.contact_search);

        grid = (TACGridView) header.findViewById(R.id.favorites_grid);
        grid.setAdapter(mGridAdapter);
        grid.setExpanded(true);
        
        setListViewListeners(inflatedView);
        setGridViewListeners();

        return inflatedView;
    }

    private void setGridViewListeners() {
        grid.setOnDragListener(dragListener);
        grid.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View v, int pos, long arg3) {
                mCallbacks.onCallContact(mGridAdapter.getItem(pos));
            }
        });
        grid.setOnItemLongClickListener(mItemLongClickListener);
    }

    private void setListViewListeners(View inflatedView) {
        mSwipeLvTouchListener = new SwipeListViewTouchListener(list, new SwipeListViewTouchListener.OnSwipeCallback() {
            @Override
            public void onSwipeLeft(ListView listView, int[] reverseSortedPositions) {
            }

            @Override
            public void onSwipeRight(ListView listView, View down) {
                down.findViewById(R.id.quick_edit).setClickable(true);
                down.findViewById(R.id.quick_discard).setClickable(true);
                down.findViewById(R.id.quick_starred).setClickable(true);

            }
        }, true, false);

        list.setOnDragListener(dragListener);
        list.setOnTouchListener(mSwipeLvTouchListener);
        list.setOnItemLongClickListener(mItemLongClickListener);

        list.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int pos, long id) {
                mSwipeLvTouchListener.openItem(view, pos, id);
            }
        });
        list.setOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (visibleItemCount > 0 && firstVisibleItem == 0 && view.getChildAt(0).getTop() == 0) {
                    // ListView scrolled at top
                }
            }
        });
    }

    OnDragListener dragListener = new OnDragListener() {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // Do nothing
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                // v.setBackgroundDrawable(null);
                break;
            case DragEvent.ACTION_DROP:
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                View view1 = (View) event.getLocalState();
                view1.setVisibility(View.VISIBLE);
            default:
                break;
            }
            return true;
        }

    };

    @Override
    public boolean onQueryTextChange(String newText) {

        // Called when the action bar search text has changed. Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        // String newFilter = !TextUtils.isEmpty(newText) ? newText : null;
        // Don't do anything if the filter hasn't actually changed.
        // Prefents restarting the loader when restoring state.
        // if (mCurFilter == null && newFilter == null) {
        // return true;
        // }
        // if (mCurFilter != null && mCurFilter.equals(newText)) {
        // return true;
        // }
        if (newText.isEmpty()) {
            getLoaderManager().restartLoader(LoaderConstants.CONTACT_LOADER, null, this);
            return true;
        }
        mCurFilter = newText;
        Bundle b = new Bundle();
        b.putString("filter", mCurFilter);
        getLoaderManager().restartLoader(LoaderConstants.CONTACT_LOADER, b, this);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        // Return false to let the SearchView perform the default action
        return false;
    }

    @Override
    public Loader<Bundle> onCreateLoader(int id, Bundle args) {
        Uri baseUri;

        Log.e(TAG, "createLoader");

        if (args != null) {
            baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(args.getString("filter")));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }
        ContactsLoader l = new ContactsLoader(getActivity(), baseUri);
        l.forceLoad();
        return l;
    }

    @Override
    public void onLoadFinished(Loader<Bundle> loader, Bundle data) {

        mListAdapter.removeAll();
        mGridAdapter.removeAll();
        ArrayList<CallContact> tmp = data.getParcelableArrayList("Contacts");
        ArrayList<CallContact> tmp2 = data.getParcelableArrayList("Starred");
        mListAdapter.addAll(tmp);
        mGridAdapter.addAll(tmp2);

    }

    @Override
    public void onLoaderReset(Loader<Bundle> loader) {
    }

    public void setHandleView(RelativeLayout handle) {

        ((ImageButton) handle.findViewById(R.id.contact_search_button)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                list.smoothScrollToPosition(0);
                search.setOnQueryTextListener(ContactListFragment.this);
                search.setIconified(false);
                mCallbacks.openDrawer();
            }
        });

    }

}
