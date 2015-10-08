package cx.ring.service;

import android.app.Service;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cx.ring.BuildConfig;
import cx.ring.history.HistoryCall;
import cx.ring.history.HistoryEntry;
import cx.ring.history.HistoryManager;
import cx.ring.history.HistoryText;
import cx.ring.model.CallContact;
import cx.ring.model.Conference;
import cx.ring.model.Conversation;
import cx.ring.model.SipCall;
import cx.ring.model.TextMessage;
import cx.ring.model.account.Account;
import cx.ring.utils.Utilities;


public class LocalService extends Service {
    static final String TAG = LocalService.class.getSimpleName();
    static public final String ACTION_CONF_UPDATE = BuildConfig.APPLICATION_ID + ".action.CONF_UPDATE";
    static public final String ACTION_ACCOUNT_UPDATE = BuildConfig.APPLICATION_ID + ".action.ACCOUNT_UPDATE";

    public static final String AUTHORITY = "cx.ring";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    private ISipService mService = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private Map<String, Conversation> conversations = new HashMap<>();
    private ArrayList<Account> all_accounts = new ArrayList<>();
    private List<Account> accounts = all_accounts;
    private List<Account> ip2ip_account = all_accounts;

    private HistoryManager historyManager;

    AccountsLoader mAccountLoader = null;

    public interface Callbacks {
        ISipService getRemoteService();
        LocalService getService();
    }
    public static class DummyCallbacks implements Callbacks {
        @Override
        public ISipService getRemoteService() {
            return null;
        }
        @Override
        public LocalService getService() {
            return null;
        }
    }
    public static final Callbacks DUMMY_CALLBACKS = new DummyCallbacks();

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        super.onCreate();
        historyManager = new HistoryManager(this);
        Intent intent = new Intent(this, SipService.class);
        startService(intent);
        bindService(intent, mConnection, BIND_AUTO_CREATE | BIND_IMPORTANT | BIND_ABOVE_CLIENT );
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        stopListener();
    }

    private final Loader.OnLoadCompleteListener<ArrayList<Account>> onAccountsLoaded = new Loader.OnLoadCompleteListener<ArrayList<Account>>() {
        @Override
        public void onLoadComplete(Loader<ArrayList<Account>> loader, ArrayList<Account> data) {
            Log.w(TAG, "AccountsLoader Loader.OnLoadCompleteListener");
            all_accounts = data;
            accounts = all_accounts.subList(0,data.size()-1);
            ip2ip_account = all_accounts.subList(data.size()-1,data.size());
            sendBroadcast(new Intent(ACTION_ACCOUNT_UPDATE));
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.w(TAG, "onServiceConnected " + className.getClassName());
            mService = ISipService.Stub.asInterface(service);
            //mBound = true;
            mAccountLoader = new AccountsLoader(LocalService.this);
            mAccountLoader.registerListener(1, onAccountsLoaded);
            mAccountLoader.startLoading();
            mAccountLoader.forceLoad();
            startListener();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.w(TAG, "onServiceDisconnected " + arg0.getClassName());
            if (mAccountLoader != null) {
                mAccountLoader.unregisterListener(onAccountsLoaded);
                mAccountLoader.cancelLoad();
                mAccountLoader.stopLoading();
            }
            //mBound = false;
            mService = null;
        }
    };

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public LocalService getService() {
            // Return this instance of LocalService so clients can call public methods
            return LocalService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(TAG, "onUnbind");
        if (mConnection != null) {
            unbindService(mConnection);
            mConnection = null;
        }
        return super.onUnbind(intent);
    }

    public ISipService getRemoteService() {
        return mService;
    }

    public List<Account> getAccounts() { return accounts; }
    public List<Account> getIP2IPAccount() { return ip2ip_account; }
    public Account getAccount(String account_id) {
        for (Account acc : all_accounts)
            if (acc.getAccountID().equals(account_id))
                return acc;
        return null;
    }

    public ArrayList<Conversation> getConversations() {
        ArrayList<Conversation> convs = new ArrayList<>(conversations.values());
        Collections.sort(convs, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation lhs, Conversation rhs) {
                return (int) ((rhs.getLastInteraction().getTime() - lhs.getLastInteraction().getTime())/1000l);
            }
        });
        return convs;
    }

    public Conversation getConversation(String id) {
        return conversations.get(id);
    }

    public Conference getConference(String id) {
        for (Conversation conv : conversations.values()) {
            Conference conf = conv.getConference(id);
            if (conf != null)
                return conf;
        }
        return null;
    }

    public Conversation getByContact(CallContact contact) {
        ArrayList<String> keys = contact.getIds();
        for (String k : keys) {
            Conversation c = conversations.get(k);
            if (c != null)
                return c;
        }
        Log.w(TAG, "getByContact failed");
        return null;
    }
    public Conversation getByCallId(String callId) {
        for (Conversation conv : conversations.values()) {
            Conference conf = conv.getConference(callId);
            if (conf != null)
                return conv;
        }
        return null;
    }

    public Conversation startConversation(CallContact contact) {
        Conversation c = getByContact(contact);
        if (c == null) {
            c = new Conversation(contact);
            conversations.put(contact.getIds().get(0), c);
        }
        return c;
    }

    public CallContact findContactByNumber(String number) {
        for (Conversation conv : conversations.values()) {
            if (conv.contact.hasNumber(number))
                return conv.contact;
        }
        return findContactByNumber(getContentResolver(), number);
    }

    public CallContact findContactById(long id) {
        return findById(getContentResolver(), id);
    }

    public Account guessAccount(CallContact c, String number) {
        number = CallContact.canonicalNumber(number);
        if (Utilities.isIpAddress(number))
            return ip2ip_account.get(0);
        /*Conversation conv = getByContact(c);
        if (conv != null) {
            return
        }*/
        return accounts.get(0);
    }

    public static final String[] DATA_PROJECTION = {
            ContactsContract.Data._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_ID,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI
    };
    public static final String[] CONTACT_PROJECTION = {
            ContactsContract.Data._ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_ID,
    };

    public static final String[] PHONELOOKUP_PROJECTION = {
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.LOOKUP_KEY,
            ContactsContract.PhoneLookup.PHOTO_ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    private static final String[] CONTACTS_PHONES_PROJECTION = {
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
    };
    private static final String[] CONTACTS_SIP_PROJECTION = {
            ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
            ContactsContract.CommonDataKinds.SipAddress.TYPE
    };
   /* private static final String[] CONTACTS_PHOTO_PROJECTION = {
            ContactsContract.CommonDataKinds.Photo.,
            ContactsContract.CommonDataKinds.SipAddress.TYPE
    };
*/
    private static final String ID_SELECTION = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?";
    private static final String KEY_SELECTION = ContactsContract.Contacts.LOOKUP_KEY + "=?";

    private static void lookupDetails(@NonNull ContentResolver res, @NonNull CallContact c) {
        Log.w(TAG, "lookupDetails " + c.getKey());

        Cursor cPhones = res.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                CONTACTS_PHONES_PROJECTION,
                ID_SELECTION,
                new String[]{String.valueOf(c.getId())}, null);
        if (cPhones != null) {
            final int iNum =  cPhones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            final int iType =  cPhones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
            while (cPhones.moveToNext()) {
                c.addNumber(cPhones.getString(iNum), cPhones.getInt(iType), CallContact.NumberType.TEL);
                Log.w(TAG,"Phone:"+cPhones.getString(cPhones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
            }
            cPhones.close();
        }

        Uri baseUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getId());
        Uri targetUri = Uri.withAppendedPath(baseUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY);
        Cursor cSip = res.query(targetUri,
                CONTACTS_SIP_PROJECTION,
                ContactsContract.Data.MIMETYPE + "=?",
                new String[]{ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE}, null);
        if (cSip != null) {
            final int iSip =  cSip.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
            final int iType =  cSip.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.TYPE);
            while (cSip.moveToNext()) {
                c.addNumber(cSip.getString(iSip), cSip.getInt(iType), CallContact.NumberType.SIP);
                Log.w(TAG, "SIP phone:" + cSip.getString(iSip));
            }
            cSip.close();
        }

        /*Cursor cPhoto = res.query(targetUri,
                CONTACTS_SIP_PROJECTION,
                ContactsContract.Data.MIMETYPE + "=?",
                new String[]{ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE}, null);
        if (cSip != null) {
            final int iSip =  cSip.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
            final int iType =  cSip.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.TYPE);
            while (cSip.moveToNext()) {
                c.addNumber(cSip.getString(iSip), cSip.getInt(iType), CallContact.NumberType.SIP);
                Log.w(TAG, "SIP phone:" + cSip.getString(iSip));
            }
            cSip.close();
        }*/
    }

    public static CallContact findByKey(@NonNull ContentResolver res, String key) {
        Log.e(TAG, "findByKey " + key);

        final CallContact.ContactBuilder builder = CallContact.ContactBuilder.getInstance();
        Cursor result = res.query( Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, key), CONTACT_PROJECTION,
                null, null, null);

        CallContact contact = null;
        if (result != null && result.moveToFirst()) {
            int iID = result.getColumnIndex(ContactsContract.Data._ID);
            int iKey = result.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
            int iName = result.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
            int iPhoto = result.getColumnIndex(ContactsContract.Data.PHOTO_ID);
            long cid = result.getLong(iID);

            Log.w(TAG, "Contact id:" + cid + " key:" + result.getString(iKey));

            builder.startNewContact(cid, result.getString(iKey), result.getString(iName), result.getLong(iPhoto));
            result.close();

            contact = builder.build();
            lookupDetails(res, contact);
        }
        return contact;
    }

     public static CallContact findById(@NonNull ContentResolver res, long id) {
         Log.e(TAG, "findById " + id);

         final CallContact.ContactBuilder builder = CallContact.ContactBuilder.getInstance();
         Cursor result = res.query(ContactsContract.Contacts.CONTENT_URI, CONTACT_PROJECTION,
                 ContactsContract.Contacts._ID + " = ?",
                 new String[]{String.valueOf(id)}, null);
         if (result == null)
             return null;

         CallContact contact = null;
         if (result.moveToFirst()) {
             int iID = result.getColumnIndex(ContactsContract.Data._ID);
             int iKey = result.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
             int iName = result.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
             int iPhoto = result.getColumnIndex(ContactsContract.Data.PHOTO_ID);
             long cid = result.getLong(iID);

             Log.w(TAG, "Contact id:" + cid + " key:" + result.getString(iKey));

             builder.startNewContact(cid, result.getString(iKey), result.getString(iName), result.getLong(iPhoto));
             contact = builder.build();
             lookupDetails(res, contact);
         }
         result.close();
         return contact;
    }
    /*
    public static int getRawContactId(@NonNull ContentResolver res, long contactId)
    {
        Cursor c= res.query(
                ContactsContract.RawContacts.CONTENT_URI,
                new String[]{ContactsContract.RawContacts._ID},
                ContactsContract.RawContacts.CONTACT_ID+"=?",
                new String[]{String.valueOf(contactId)},
                null);
        if (c == null)
            return -1;
        if (c.moveToFirst()) {
            int rawContactId = c.getInt(c.getColumnIndex(ContactsContract.RawContacts._ID));
            Log.d(TAG, "Contact Id: " + contactId + " Raw Contact Id: " + rawContactId);
            return rawContactId;
        }
        c.close();
        return -1;
    }
*/
    @NonNull
    public static CallContact findContactBySipNumber(@NonNull ContentResolver res, String number) {
        final CallContact.ContactBuilder builder = CallContact.ContactBuilder.getInstance();
        Cursor result = res.query(ContactsContract.Data.CONTENT_URI,
                DATA_PROJECTION,
                ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?",
                new String[]{number, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE}, null);
        if (result == null)  {
            Log.w(TAG, "findContactBySipNumber " + number + " can't find contact.");
            return CallContact.ContactBuilder.buildUnknownContact(number);
        }
        int iID = result.getColumnIndex(ContactsContract.Data._ID);
        int icID = result.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID);
        int iKey = result.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
        int iName = result.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
        int iPhoto = result.getColumnIndex(ContactsContract.Data.PHOTO_ID);
        int iPhotoThumb = result.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI);

        ArrayList<CallContact> contacts = new ArrayList<>(1);
        while (result.moveToNext()) {
            long cid = result.getLong(icID);
            long id = result.getLong(iID);
            builder.startNewContact(cid, result.getString(iKey), result.getString(iName), result.getLong(iPhoto));
            CallContact contact = builder.build();
            //Log.w(TAG, "findContactBySipNumber " + number + " found name:" + contact.getDisplayName() + " id:" + contact.getId() + " key:" + contact.getKey() + " rawid:"+getRawContactId(res, contact.getId()) + " rid:"+id + " photo:"+result.getLong(iPhoto) + " thumb:" + result.getString(iPhotoThumb));
            lookupDetails(res, contact);
            contacts.add(contact);
        }
        result.close();

        //lookupDetails(res, contact);
        /*if (contact == null) {
            Log.w(TAG, "Can't find contact with number " + number);
            contact = CallContact.ContactBuilder.buildUnknownContact(number);
        }*/
        if (contacts.isEmpty()) {
            Log.w(TAG, "findContactBySipNumber " + number + " can't find contact.");
            return CallContact.ContactBuilder.buildUnknownContact(number);
        }
        return contacts.get(0);
    }

    @NonNull
    public static CallContact findContactByNumber(@NonNull ContentResolver res, String number) {
        //Log.w(TAG, "findContactByNumber " + number);

        final CallContact.ContactBuilder builder = CallContact.ContactBuilder.getInstance();
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor result = res.query(uri, PHONELOOKUP_PROJECTION, null, null, null);
        if (result == null)  {
            Log.w(TAG, "findContactByNumber " + number + " can't find contact.");
            return findContactBySipNumber(res, number);
        }
        if (!result.moveToFirst())  {
            result.close();
            Log.w(TAG, "findContactByNumber " + number + " can't find contact.");
            return findContactBySipNumber(res, number);
        }
        int iID = result.getColumnIndex(ContactsContract.Contacts._ID);
        int iKey = result.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
        int iName = result.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        int iPhoto = result.getColumnIndex(ContactsContract.Contacts.PHOTO_ID);
        builder.startNewContact(result.getLong(iID), result.getString(iKey), result.getString(iName), result.getLong(iPhoto));
        result.close();
        CallContact contact = builder.build();
        lookupDetails(res, contact);
        /*if (contact == null) {
            Log.w(TAG, "Can't find contact with number " + number);
            contact = CallContact.ContactBuilder.buildUnknownContact(number);
        }*/
        Log.w(TAG, "findContactByNumber " + number + " found " + contact.getDisplayName());

        return contact;
    }

    private class ConversationLoader extends AsyncTask<Void, Void, Map<String, Conversation>> {
        private final ContentResolver cr;

        public ConversationLoader(Context c) {
            cr = c.getContentResolver();
        }

        private CallContact getByNumber(HashMap<String, CallContact> cache, String number) {
            if (number == null || number.isEmpty())
                return null;
            number = CallContact.canonicalNumber(number);
            CallContact c = cache.get(number);
            if (c == null) {
                c = findContactByNumber(cr, number);
                //if (c != null)
                cache.put(number, c);
            }
            return c;
        }

        Pair<HistoryEntry, HistoryCall> findHistoryByCallId(final Map<String, Conversation> confs, String id) {
            for (Conversation c : confs.values()) {
                Pair<HistoryEntry, HistoryCall> h = c.findHistoryByCallId(id);
                if (h != null)
                    return h;
            }
            return null;
        }

        @Override
        protected Map<String, Conversation> doInBackground(Void... params) {
            List<HistoryCall> history = null;
            List<HistoryText> historyTexts = null;
            Map<String, Conference> confs = null;
            final Map<String, Conversation> ret = new HashMap<>();
            final LongSparseArray<CallContact> localContactCache = new LongSparseArray<>(64);
            final HashMap<String, CallContact> localNumberCache = new HashMap<>(64);


            try {
                history = historyManager.getAll();
                historyTexts = historyManager.getAllTextMessages();
                confs = mService.getConferenceList();
            } catch (RemoteException | SQLException e) {
                e.printStackTrace();
            }

            for (HistoryCall call : history) {
                //Log.w(TAG, "History call : " + call.getNumber() + " " + call.call_start + " " + call.call_end + " " + call.getEndDate().toString());
                CallContact contact;
                if (call.getContactID() <= CallContact.DEFAULT_ID) {
                    contact = getByNumber(localNumberCache, call.getNumber());
                } else {
                    contact = localContactCache.get(call.getContactID());
                    if (contact == null) {
                        contact = findById(cr, call.getContactID());
                        if (contact != null)
                            contact.addPhoneNumber(call.getNumber(), 0);
                        else {
                            Log.w(TAG, "Can't find contact with id " + call.getContactID());
                            contact = getByNumber(localNumberCache, call.getNumber());
                        }
                        localContactCache.put(contact.getId(), contact);
                    }
                }

                Map.Entry<String, Conversation> merge = null;
                for (Map.Entry<String, Conversation> ce : ret.entrySet()) {
                    Conversation c = ce.getValue();
                    if ((contact.getId() > 0 && contact.getId() == c.contact.getId()) || c.contact.hasNumber(call.getNumber())) {
                        merge = ce;
                        break;
                    }
                }
                if (merge != null) {
                    Conversation c = merge.getValue();
                    //Log.w(TAG, "        Join to " + merge.getKey() + " " + c.getContact().getDisplayName() + " " + call.getNumber());
                    if (c.getContact().getId() <= 0 && contact.getId() > 0) {
                        c.contact = contact;
                        ret.remove(merge.getKey());
                        ret.put(contact.getIds().get(0), c);
                    }
                    c.addHistoryCall(call);
                    continue;
                }
                String key = contact.getIds().get(0);
                if (ret.containsKey(key)) {
                    ret.get(key).addHistoryCall(call);
                } else {
                    Conversation c = new Conversation(contact);
                    c.addHistoryCall(call);
                    ret.put(key, c);
                }
            }

            for (HistoryText htext : historyTexts) {
                CallContact contact;

                if (htext.getContactID() <= CallContact.DEFAULT_ID) {
                    contact = getByNumber(localNumberCache, htext.getNumber());
                } else {
                    contact = localContactCache.get(htext.getContactID());
                    if (contact == null) {
                        contact = findById(cr, htext.getContactID());
                        if (contact != null)
                            contact.addPhoneNumber(htext.getNumber(), 0);
                        else {
                            Log.w(TAG, "Can't find contact with id " + htext.getContactID());
                            contact = getByNumber(localNumberCache, htext.getNumber());
                        }
                        localContactCache.put(contact.getId(), contact);
                    }
                }

                Pair<HistoryEntry, HistoryCall> p = findHistoryByCallId(ret, htext.getCallId());

                if (contact == null && p != null)
                    contact = p.first.getContact();
                if (contact == null)
                    continue;

                TextMessage msg = new TextMessage(htext);
                msg.setContact(contact);

                if (p  != null) {
                    if (msg.getNumber() == null || msg.getNumber().isEmpty())
                        msg.setNumber(p.second.getNumber());
                    p.first.addTextMessage(msg);
                }

                String key = contact.getIds().get(0);
                if (ret.containsKey(key)) {
                    ret.get(key).addTextMessage(msg);
                } else {
                    Conversation c = new Conversation(contact);
                    c.addTextMessage(msg);
                    ret.put(key, c);
                }
            }

            /*context.clear();
            ctx = null;*/
            for (Map.Entry<String, Conference> c : confs.entrySet()) {
                //Log.w(TAG, "ConversationLoader handling " + c.getKey() + " " + c.getValue().getId());
                Conference conf = c.getValue();
                ArrayList<SipCall> calls = conf.getParticipants();
                if (calls.size() >= 1) {
                    CallContact contact = calls.get(0).getContact();
                    //Log.w(TAG, "Contact : " + contact.getId() + " " + contact.getDisplayName());
                    Conversation conv = null;
                    ArrayList<String> ids = contact.getIds();
                    for (String id : ids) {
                        //Log.w(TAG, "    uri attempt : " + id);
                        conv = ret.get(id);
                        if (conv != null) break;
                    }
                    if (conv != null) {
                        //Log.w(TAG, "Adding conference to existing conversation ");
                        conv.current_calls.add(conf);
                    } else {
                        conv = new Conversation(contact);
                        conv.current_calls.add(conf);
                        ret.put(ids.get(0), conv);
                    }
                }
            }
            for (Conversation c : ret.values())
                Log.w(TAG, "Conversation : " + c.getContact().getId() + " " + c.getContact().getDisplayName() + " " + c.getContact().getPhones().get(0).getNumber() + " " + c.getLastInteraction().toString());
            return ret;
        }
    }

    private void updated(Map<String, Conversation> res) {
        Log.w(TAG, "Conversation list updated");
        conversations = res;
        sendBroadcast(new Intent(ACTION_CONF_UPDATE));
    }

    public class AccountsLoader extends AsyncTaskLoader<ArrayList<Account>> {
        public static final String ACCOUNTS = "accounts";
        public static final String ACCOUNT_IP2IP = "IP2IP";
        public AccountsLoader(Context context) {
            super(context);
            Log.w(TAG, "AccountsLoader constructor");
        }
        @SuppressWarnings("unchecked")
        @Override
        public ArrayList<Account> loadInBackground() {
            Log.w(TAG, "AccountsLoader loadInBackground");
            ArrayList<Account> accounts = new ArrayList<>();
            Account IP2IP = null;
            try {
                ArrayList<String> accountIDs = (ArrayList<String>) mService.getAccountList();
                Map<String, String> details;
                ArrayList<Map<String, String>> credentials;
                Map<String, String> state;
                for (String id : accountIDs) {
                    details = (Map<String, String>) mService.getAccountDetails(id);
                    state = (Map<String, String>) mService.getVolatileAccountDetails(id);
                    if (id.contentEquals(ACCOUNT_IP2IP)) {
                        IP2IP = new Account(ACCOUNT_IP2IP, details, new ArrayList<Map<String, String>>(), state); // Empty credentials
                        //accounts.add(IP2IP);
                        continue;
                    }
                    credentials = (ArrayList<Map<String, String>>) mService.getCredentials(id);
                /*for (Map.Entry<String, String> entry : State.entrySet()) {
                    Log.i(TAG, "State:" + entry.getKey() + " -> " + entry.getValue());
                }*/
                    Account tmp = new Account(id, details, credentials, state);
                    accounts.add(tmp);
                    // Log.i(TAG, "account:" + tmp.getAlias() + " " + tmp.isEnabled());

                }
            } catch (RemoteException | NullPointerException e) {
                Log.e(TAG, e.toString());
            }
            accounts.add(IP2IP);
            return accounts;
        }
    }

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    Log.w(TAG, "ConnectivityManager.CONNECTIVITY_ACTION " + " " + intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO) + " " + intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO));
                    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo ni = cm.getActiveNetworkInfo();
                    Log.w(TAG, "ActiveNetworkInfo: " + (ni == null ? "null" : ni.toString()));
                    break;
                case ConfigurationManagerCallback.ACCOUNT_STATE_CHANGED:
                    Log.w(TAG, "Received " + intent.getAction() + " " + intent.getStringExtra("Account") + " " + intent.getStringExtra("State") + " " + intent.getIntExtra("code", 0));
                    //accountStateChanged(intent.getStringExtra("Account"), intent.getStringExtra("State"), intent.getIntExtra("code", 0));
                    for (Account a : accounts) {
                        if (a.getAccountID().contentEquals(intent.getStringExtra("Account"))) {
                            a.setRegistrationState(intent.getStringExtra("State"), intent.getIntExtra("code", 0));
                            //notifyDataSetChanged();
                            sendBroadcast(new Intent(ACTION_ACCOUNT_UPDATE));
                            break;
                        }
                    }
                    break;
                case ConfigurationManagerCallback.ACCOUNTS_CHANGED:
                    Log.w(TAG, "Received" + intent.getAction());
                    //accountsChanged();
                    mAccountLoader.onContentChanged();
                    mAccountLoader.startLoading();
                    break;
                case CallManagerCallBack.INCOMING_TEXT:
                case ConfigurationManagerCallback.INCOMING_TEXT:
                    TextMessage txt = intent.getParcelableExtra("txt");
                    String call = txt.getCallId();
                    if (call != null && !call.isEmpty()) {
                        Conversation conv = getByCallId(call);
                        conv.addTextMessage(txt);
                        /*Conference conf = conv.getConference(call);
                        conf.addSipMessage(txt);
                        Conversation conv = getByContact(conf.)*/
                    } else {
                        CallContact contact = findContactByNumber(txt.getNumber());
                        Conversation conv = startConversation(contact);
                        txt.setContact(conv.getContact());
                        Log.w(TAG, "New text messsage " + txt.getAccount() + " " + txt.getContact().getId() + " " + txt.getMessage());
                        conv.addTextMessage(txt);
                    }
                    sendBroadcast(new Intent(ACTION_CONF_UPDATE));
                    break;
                default:
                    Log.w(TAG, "onReceive " + intent.getAction() + " " + intent.getDataString());
                    new ConversationLoader(context){
                        @Override
                        protected void onPostExecute(Map<String, Conversation> res) {
                            updated(res);
                        }
                    }.execute();
            }
        }
    };

    public void startListener() {
        final WeakReference<LocalService> self = new WeakReference<>(this);
        new ConversationLoader(this){
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Log.w(TAG, "onPreExecute");
            }

            @Override
            protected void onPostExecute(Map<String, Conversation> res) {
                Log.w(TAG, "onPostExecute");
                LocalService this_ = self.get();
                if (this_ != null)
                    this_.updated(res);
                else
                    Log.e(TAG, "AsyncTask finished but parent is destroyed..");
            }
        }.execute();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConfigurationManagerCallback.ACCOUNT_STATE_CHANGED);
        intentFilter.addAction(ConfigurationManagerCallback.ACCOUNTS_CHANGED);
        intentFilter.addAction(ConfigurationManagerCallback.INCOMING_TEXT);

        intentFilter.addAction(CallManagerCallBack.INCOMING_CALL);
        intentFilter.addAction(CallManagerCallBack.INCOMING_TEXT);
        intentFilter.addAction(CallManagerCallBack.CALL_STATE_CHANGED);
        intentFilter.addAction(CallManagerCallBack.CONF_CREATED);
        intentFilter.addAction(CallManagerCallBack.CONF_CHANGED);
        intentFilter.addAction(CallManagerCallBack.CONF_REMOVED);

        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(receiver, intentFilter);
    }

    public void stopListener() {
        unregisterReceiver(receiver);
    }

}
