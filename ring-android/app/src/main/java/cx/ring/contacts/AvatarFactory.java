/*
 * Copyright (C) 2004-2020 Savoir-faire Linux Inc.
 *
 * Author: Pierre Duchemin <pierre.duchemin@savoirfairelinux.com>
 * Author: Adrien Béraud <adrien.beraud@savoirfairelinux.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package cx.ring.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;

import java.util.List;

import cx.ring.model.Account;
import cx.ring.model.CallContact;
import cx.ring.utils.BitmapUtils;
import cx.ring.views.AvatarDrawable;
import io.reactivex.Single;

public class AvatarFactory {

    public static final int SIZE_AB = 36;
    public static final int SIZE_NOTIF = 48;
    public static final int SIZE_PADDING = 8;

    private AvatarFactory() {}

    public static void loadGlideAvatar(ImageView view, CallContact contact) {
        getGlideAvatar(view.getContext(), contact).into(view);
    }

    public static Single<Drawable> getAvatar(Context context, CallContact contact, boolean presence) {
        return Single.fromCallable(() ->
                new AvatarDrawable.Builder()
                        .withContact(contact)
                        .withCircleCrop(true)
                        .withPresence(presence)
                        .build(context));
    }
    public static Single<Drawable> getAvatar(Context context, List<CallContact> contacts, boolean presence) {
        return Single.fromCallable(() ->
                new AvatarDrawable.Builder()
                        .withContacts(contacts)
                        .withCircleCrop(true)
                        .withPresence(presence)
                        .build(context));
    }
    public static Single<Drawable> getAvatar(Context context, CallContact contact) {
        return getAvatar(context, contact, true);
    }
    public static Single<Drawable> getAvatar(Context context, List<CallContact> contacts) {
        return getAvatar(context, contacts, true);
    }

    public static Single<Bitmap> getBitmapAvatar(Context context, List<CallContact> contacts, int size, boolean presence) {
        return getAvatar(context, contacts, presence)
                .map(d -> BitmapUtils.drawableToBitmap(d, size));
    }
    public static Single<Bitmap> getBitmapAvatar(Context context, CallContact contact, int size, boolean presence) {
        return getAvatar(context, contact, presence)
                .map(d -> BitmapUtils.drawableToBitmap(d, size));
    }
    public static Single<Bitmap> getBitmapAvatar(Context context, CallContact contact, int size) {
        return getBitmapAvatar(context, contact, size, true);
    }

    public static Single<Bitmap> getBitmapAvatar(Context context, Account account, int size) {
        return AvatarDrawable.load(context, account)
                .map(d -> BitmapUtils.drawableToBitmap(d, size));
    }

    private static Drawable getDrawable(Context context, Bitmap photo, String profileName, String username, String id) {
        return new AvatarDrawable.Builder()
                .withPhoto(photo)
                .withName(TextUtils.isEmpty(profileName) ? username : profileName)
                .withId(id)
                .withCircleCrop(true)
                .build(context);
    }

    public static void clearCache() {
    }

    private static <T> RequestBuilder<T> getGlideRequest(Context context, RequestBuilder<T> request, Bitmap photo, String profileName, String username, String id) {
        return request.load(getDrawable(context, photo, profileName, username, id));
    }

    private static RequestBuilder<Drawable> getGlideAvatar(Context context, RequestManager manager, CallContact contact) {
        return getGlideRequest(context, manager.asDrawable(), (Bitmap)contact.getPhoto(), contact.getProfileName(), contact.getUsername(), contact.getPrimaryNumber());
    }

    private static RequestBuilder<Drawable> getGlideAvatar(Context context, CallContact contact) {
        return getGlideAvatar(context, Glide.with(context), contact);
    }
}
