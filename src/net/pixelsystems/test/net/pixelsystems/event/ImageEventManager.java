package net.pixelsystems.test.net.pixelsystems.event;

import android.app.Activity;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

/**
 * Copyright 1/21/11 William May
 * This file is part of CameraTest.
 * CameraTest is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * CameraTest is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Foobar.
 * If not, see http://www.gnu.org/licenses/.
 *
 * This software is derived from Picasa Photo Uploader by Jan Peter Hooiveld
 * more information on his project can be found at http://code.google.com/p/picasaphotouploader/

/**
 * Created by IntelliJ IDEA.
 * User: William 'pr0cs' May
 * Date: 1/21/11
 * Time: 10:56 AM
 * Handles scenario where we want to be notified that a new image was saved/added to the images table.
 * By listening to these type of events we can bypass creating another dummy file when the camera intent
 * has completed since adding Intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri) can add 2+ images
 * 1) image stored by the Camera intent (files in the DCIM directory)
 * 2) image thumbnail also stored/created by the intent
 * 3) the file specified in the Uri
 *
 */
public class ImageEventManager {
    private int maxId;
    private Activity _source;
    private ImageTableObserver camera;
    private ImageEventListener _listener;

    /**
     * Constructor
     * @param source Activity interested in using this manager, providing necessary connection to the content
     * resolver, managed queries, etc
     * @param listener that will be notified when a new image has been saved/applied to the image table after
     * the camera intent has completed
     */
    public ImageEventManager(Activity source,ImageEventListener listener) {
        _source = source;
        _listener = listener;
        // store highest image id from database in application
        setMaxIdFromDatabase();

        // register camera observer
        camera = new ImageTableObserver(new Handler());
        _source.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, camera);
    }

    /**
     * Store highest image id from image table
     */
    private void setMaxIdFromDatabase() {
        String columns[] = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MINI_THUMB_MAGIC};
        Cursor cursor = _source.managedQuery(MediaStore.Images.Media.INTERNAL_CONTENT_URI, columns, null, null, MediaStore.Images.Media._ID + " DESC");
        maxId = cursor.moveToFirst() ? cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID)) : -1;
    }

    /**
     * Set highest image id
     *
     * @param maxId New value for maxId
     */
    public void setMaxId(int maxId) {
        this.maxId = maxId;
    }

    /**
     * Get highest image id
     *
     * @return Highest id
     */
    public int getMaxId() {
        return maxId;
    }

    /**
     * internal class that watches for new images appearing in the image table and notifies the attached
     * listener that a new image has arrived.
     */
    private class ImageTableObserver extends ContentObserver {
        /**
         * Constructor
         *
         * @param handler Handler for this class
         */
        public ImageTableObserver(Handler handler) {
            super(handler);

        }

        /**
         * This function is fired when a change occurs on the image table
         *
         * @param selfChange
         */
        @Override
        public void onChange(boolean selfChange) {
            // get latest image id
            ImageLatest latestImage = new ImageLatest();
            int imageId = latestImage.getId();

            // if id is -1 it means no record was found or it was a update/delete instead of insert
            if (imageId == -1) {
                return;
            }

            // get image item
            ImageItem item = latestImage.getLatestItem();

            // if no image item returned abort
            if (item == null) {
                return;
            }

            // notify the listener that a new image has arrived
            _listener.newImageAvailable(item.imagePath);
        }
    }

    /**
     * returns the media id of the new item if possible
     */
    private class ImageLatest {
        private int latestId;
        /**
         * Get highest id from database
         *
         * @return highest image id in database or -1 if conditions fail
         */
        private int getId() {
            String[] columns = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.ORIENTATION};
            Cursor cursor = _source.managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, MediaStore.Images.Media._ID + " DESC");
            if(cursor==null)
                return -1;
            // check if table has any rows at all
            if (!cursor.moveToFirst()) {
                cursor.close();
                return -1;
            }

            // get latest id from db and stored id in application
            latestId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));
            int maxId = getMaxId();

            // if id from db is equal or lower to stored id it means user changed or
            // deleted somewhere in table so store the new highest id and return
            if (latestId <= maxId) {
                setMaxId(latestId);
                cursor.close();
                return -1;
            }

            // If orientation is null it means new image is not a photo but we will
            // store highest id and return
            String orientation = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION));

            if (orientation == null) {
                setMaxId(latestId);
                cursor.close();
                return -1;
            }

            // store latest id in application
            setMaxId(latestId);

            cursor.close();

            // return latest id
            return latestId;
        }

        /**
         * The problem is that the observer is invoked multiple times when a photo is
         * taken with the camera. It could be that on the first time the observer is
         * triggered the record is already in the database but the file isn't
         * completely written to the sdcard. That's why we need to check if the field
         * MINI_THUMB_MAGIC is not null. If this field is not null it means the photo
         * was written to sdcard completely and we can start our upload.
         *
         * @return Image queue item
         */

        private ImageItem getLatestItem() {
            // set vars
            ImageItem item = null;
            String columns[] = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MINI_THUMB_MAGIC};

            // loop until break
            while (true) {
                // get latest image from table
                Uri image = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, latestId);
                Cursor cursor = _source.managedQuery(image, columns, null, null, null);
                if(cursor == null)
                        break;
                // check if cursor has rows, if not break and exit loop
                if (cursor.moveToFirst()) {
                    // get thumbnail field
                    String imageThumb = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.MINI_THUMB_MAGIC));

                    // if thumbnail field is not null it means image is written to sdcard
                    // create new image item and break loop otherwise restart loop to check again
                    if (imageThumb != null) {
                        item = new ImageItem();
                        item.imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));
                        item.imagePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                        item.imageName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
                        item.imageType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE));
                        item.imageSize = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media.SIZE));
                        cursor.close();
                        break;
                    }
                } else {
                    cursor.close();
                    break;
                }
            }
            return item;
        }
    }

    private class ImageItem {
        public Integer imageId;
        public String imagePath;
        public String imageName;
        public String imageType;
        public int imageSize;
    }

}
