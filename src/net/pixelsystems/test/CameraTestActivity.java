package net.pixelsystems.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import net.pixelsystems.test.net.pixelsystems.event.ImageEventListener;
import net.pixelsystems.test.net.pixelsystems.event.ImageEventManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Set;

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
 */


/** This class is based off a myriad of examples that I read covering capturing camera images from an Android device
 * using the built in Camera intent rather than writing your own camera surface/control class
 * some articles that I used as a reference are:
 *
 *
 * http://achorniy.wordpress.com/2010/04/26/howto-launch-android-camera-using-intents/
 * Andrey Chorniy's article discusses a great framework on how to use the camera intent, sadly his example does not
 * take into account some UI enhancements can modify the results of the intent.  So while his code works perfect in
 * the emulator it does not work on a lot of devices and still will create multiple copies of the same image on the device
 *
 * http://www.jondev.net/articles/Capturing,_Saving,_and_Displaying_an_Image_in_Android_%281.5,_1.6,_2.0,_2.1,_2.2,_Sense_UI_-_Hero%29
 * Jon Simon's example was enlightening in that it pointed out that some device manufacturer's UI skins were interfering
 * and manipulating the results from the Camera intent.  The project that I was working on at the time was having difficulty
 * getting images into the app on HTC type devices, this is because SenseUI (from HTC) was giving totally different
 * results than a lot of other generally accepted ways to use the Camera intent.
 *
 * http://blog.tacticalnuclearstrike.com/tttumblr/
 * Fredrik Leijon's tumblr application clued me into how to get the image from the camera intent to save on all devices
 * at this point I was really close with a good fix for my app but wasn't thrilled with having duplicate images.
 * the code will function but will create one image at the Uri specified in the intent's extras AND a file saved
 * at the default file location DCIM (at least on my Samsung Galaxy S).  I didn't really need 2 images and I figured
 * my users wouldn't be thrilled about having to clean up the extra files even if I was able to clean up one of the
 * files that was specified in the URI
 *
 * http://code.google.com/p/picasaphotouploader/
 * I discovered Jan Peter Hooiveld's project on Google code and though I could modify it to use it to act as a
 * simple notifier when a new image appeared after the camera intent completed.  I was right, this method should
 * work for ALL phones AND not create duplicate images.
 *
 */

public class CameraTestActivity extends Activity implements ImageEventListener {
    public static int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    public static int CAPTURE_HTC_IMAGE_ACTIVITY_REQUEST_CODE = 2;
    public static int TAKE_PHOTO = 3;
    public static int CAPTURE_WITH_LISTENER=4;
    public static String CAMERA_URI = "CAMERA_URI";
    private Uri imageUri;
    private File htcFile;
    private Uri htcUri;
    private File selFile;
    private Uri selUri;
    private ImageEventManager _iem;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button camButton = (Button) findViewById(R.id.IMPORT_FROM_CAMERA);
        Button senseUIButton = (Button) findViewById(R.id.IMPORT_FROM_HTC_CAMERA);
        Button selectButton = (Button) findViewById(R.id.IMPORT_FROM_SELECT);
        Button listenerButton = (Button) findViewById(R.id.IMPORT_FROM_LISTENER);

        /* set up the most simple scenario, launching via the camera intent
           this really should work but it's a lot more complicated than it needs to be
           this is because in my opinion the camera intent/api in Android is still evolving.
           The problem with using the code below is that the results are different on a variety of devices
           some will be able to use the Uri supplied in the EXTRA_OUTPUT, some will return only the bitmap
         */
        camButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //define the file-name to save photo taken by Camera activity
                String fileName = "simple-photo-name.jpg";
                //create parameters for Intent with filename
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, fileName);
                values.put(MediaStore.Images.Media.DESCRIPTION, "Image capture by camera");
                //imageUri is the current activity attribute, define and save it for later usage (also in onSaveInstanceState)
                imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                //create new Intent
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
            }
        });

        /* the following code works for SenseUI type devices (because HTC's SenseUI modifies the output in that
        the actual intent result is a pointer to the bitmap data, not the actual image file saved.
        This took me a *LONG* time to figure out.  Sadly the bitmap data that is returned is simply a thumbnail, so
        it's value is really limited.  This was difficult to test because I don't have an HTC device and had to rely
        on my friend Jesse Virgil to test and prototype the results for me
         */
        senseUIButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                String rawFile = "SENSE_UI_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
                htcFile = new File(Environment.getExternalStorageDirectory(), rawFile);
                htcUri = Uri.fromFile(htcFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, htcUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, CAPTURE_HTC_IMAGE_ACTIVITY_REQUEST_CODE);
            }
        });

        /* we're getting close here with this code, the file can be captured on all devices (EXTRA_OUTPUT)
            not because the code below does much different than the above 2 methods but more because we can
            the media AFTER the intent has closed so the file appears in time to use it.
            NOTE: befor the end of the constructor I put in code to handle the scenario where the user
            is holding the phone in portrait to take the actual picture but once the picture is captured and the
            device shows the 'save image' screen (which in all situations I've seen) is in Landscape, the Activity
             will be started again, I cache the Uri filename so we can still decyper the image filename
         */
        selectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //define the file-name to save photo taken by Camera activity
                String rawFile = "WORKING_CAMERA_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
                //create parameters for Intent with filename
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, rawFile);
                values.put(MediaStore.Images.Media.DESCRIPTION, "Image capture by camera");
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "Photo");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

                selFile = new File(Environment.getExternalStorageDirectory(), rawFile);
                selUri = Uri.fromFile(selFile);

                //create new Intent
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, selUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        });

        /**
         * this is the best solution, we don't have to enter any extra junk for the camera intent, in fact the intent
         * is stupidly easy, no extra files, no output parameters no ContentValues (unless you really care about them)
         * If all you want to do is find the name of the camera image stored in the media table then we can use the
         * ImageEventManager code to notify us when the file was created
         * We need to include a flag to be aware of when a new image appears (since our app could be in the background
         * still running and the user takes a picture in another app, we don't want to handle it in that situation.
         * the _listeningForImage flag could have been moved to the ImageEventManager as well
         */
        listenerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //create new Intent
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                //String rawFile = "WORKING_CAMERA_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
                //selFile = new File(Environment.getExternalStorageDirectory(), rawFile);
                //selUri = Uri.fromFile(selFile);
                //intent.putExtra(MediaStore.EXTRA_OUTPUT, selUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                //intent.putExtra(MediaStore.EXTRA_MEDIA_TITLEEXTRA_VIDEO_QUALITY, 1);
                _iem.listenForCameraEvents();
                startActivityForResult(intent, CAPTURE_WITH_LISTENER);
                if(true)
                        return;

                 //define the file-name to save photo taken by Camera activity
                String rawFile = "WORKING_CAMERA_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
                //create parameters for Intent with filename
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, rawFile);
                values.put(MediaStore.Images.Media.DESCRIPTION, "Image capture by camera");
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "Photo");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

                selFile = new File(Environment.getExternalStorageDirectory(), rawFile);
                selUri = Uri.fromFile(selFile);

                //create new Intent
                //Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, selUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                _iem.listenForCameraEvents();
                startActivityForResult(intent, CAPTURE_WITH_LISTENER);

            }
        });


        // start the event manager for the 'listener' button
        // we don't really need to keep track of _iem but if the ImageEventManager class
        // was expanded in the future we would have access to it by caching it with _iem.
        _iem = new ImageEventManager(this,this);


        /* this is just demonstrating how to handle the scenarios (non-ImageEventManager) where the user
           is taking a picture in portrait mode then rotates the camera to read the save/discard screen after the camera
           takes the image.  If the phone is rotated then onCreate() is called again and we LOSE our Uri and filename
           so we aren't able to find out the filename.
         */
        if (savedInstanceState != null)
            // restore the image filename from the saved instance state
            if (savedInstanceState.containsKey(CAMERA_URI)) {
                String filename = savedInstanceState.getString(CAMERA_URI);
                selFile = new File(filename);
                selUri = Uri.fromFile(selFile);
                handlePhotoEvent();
            }
    }


    /**
     * this is part of the ImageEventListener, it is called when the ImageEventManager has gotten the image path
     * for us.
     * @param path path to the actual image file as added to the image table
     */
    @Override
    public void newImageAvailable(String path){
        showAlert(this,"Result!","New Camera image:\n"+path);
    }

    /**
     * simple utility method that spits out a AlertDialog with a developer defined title and message
     * @param context context
     * @param title title of the alert dialog box
     * @param message message inside the dialog box
     */
    public static void showAlert(Context context, String title, String message) {
        AlertDialog.Builder alt_bld = new AlertDialog.Builder(context);
        alt_bld.setMessage(message);
        AlertDialog alert = alt_bld.create();
        alert.setTitle(title);
        alert.setIcon(android.R.drawable.ic_dialog_alert);
        alert.setButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        alert.show();
    }

    /**
     * used so that we can cache the state of the image filename in case the user rotates the phone to confirm
     * the save/discard image in the camera app.
     * @param bundle
     */
    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (selUri != null)
            bundle.putString(CAMERA_URI, selFile.getPath());
    }

    /**
     * called when one of our activities (camera buttons) actually completes
     * @param requestCode which camera activity completed
     * @param resultCode what happened user cancelled/quit or user confirmed the activity
     * @param data the data as a rsult of the activity completing
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                /* what makes the result confusing is that on some devices data is null (which goes
                against what most articles suggest should happen), we can try and use the cached imageUri
                too but again, on some devices this is also not valid (HTC sense interfering)
                 */
                if (data == null) {
                    File file = convertImageUriToFile(imageUri, this);
                    showAlert(this, "Picture Captured(Uri not provided)", "Path: " + file.getPath());
                } else {
                    Uri dataUri = data.getData();
                    File file = convertImageUriToFile(dataUri, this);
                    showAlert(this, "Picture Captured(Uri provided with Data)", "Path: " + file.getPath());
                }

            } else if (resultCode == RESULT_CANCELED) {
                showAlert(this, "Picture Not Captured(Cancelled)", "User cancelled capturing a picture");
            } else {
                showAlert(this, "Picture Not Captured(Cancelled)", "Unknown result code");
            }
        }else if(requestCode==CAPTURE_WITH_LISTENER){
            /* very simple, which makes the listener the best choice, just make sure to turn off listening for image
                when you're not interested anymore
             */
            switch (resultCode) {
                case RESULT_OK:
                   // handlePhotoEvent();
                    _iem.cameraIntentComplete();
                    break;
                case RESULT_CANCELED:
                    _iem.ignoreCameraEvents();
                    break;
            }
        } else if (requestCode == CAPTURE_HTC_IMAGE_ACTIVITY_REQUEST_CODE) {
            /* the HTC code is a bit of a disaster, we try and handle all the situations like
            CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE above but the bitmap actually returned is thumbnail, which is
            pretty useless all you really need is a tiny image.  Again, only certain devices will return
            the actual thumbnail image so this solution won't work for everything
             */
            switch (resultCode) {
                case RESULT_OK:
                    setupImage(data);
                    break;
            }
        } else if (requestCode == TAKE_PHOTO) {
            /* this code is easy to read but mostly because we move the intelligence out into a nice method
            the reason why this code works for most of the time is the that we scan (insert) the image after the
            camera intent completes, the only problem there is we get 2+ images, one in the Uri supplied to the EXTRA_OUPUT
            and one saved by the camera intent, you might not like to have 2 duplicate images (plus sometimes a thumbnail too
            is saved on some devices so sometimes 3 duplicate images)
             */
            switch (resultCode) {
                case RESULT_OK:
                    if (selUri == null) {
                        // orientation may have changed, file should be handled in code for saved instance restore
                        return;
                    }
                    handlePhotoEvent();
                    break;
            }
        }
    }

    /*
    simple helper method for TAKE_PHOTO event.  basically the magic of this method is done in the Uri.parse call
    that actually can be used to
     */
    private void handlePhotoEvent() {
        try {
            File f = new File(selUri.getPath());

            selUri = Uri
                    .parse(android.provider.MediaStore.Images.Media
                            .insertImage(getContentResolver(), f
                                    .getAbsolutePath(), null, null));
            if(!f.delete())// delete the thumbnail image
                showAlert(this,"Delete error","could not delete the thumbnail:"+f.getPath());
            File result = convertImageUriToFile(selUri, this);
            showAlert(this, "Result", "check path for:" + selFile.getPath() + "\nand internal image of:" + result.getPath());
            selUri = null;
            selFile = null;
        } catch (Exception ex) {
            showAlert(this, "Error!", "problem encountered:\n" + ex.getMessage());
            selUri = null;
            selFile = null;
        }
    }


    /**
     * Code that attempts to handle camera intent results from devices with a skin that influences the intent's results
     * (HTC Sense) and for cameras that follow the common workflow.  sadly HTC type devices still don't have
     * access to the real file saved on disk and only return the bitmap image data for the captured image, which is
     * pretty useless
     * @param data intent data from the onActivityResult()
     * @return Bitmap (not really important for this example, left in for reference)
     */
    public Bitmap setupImage(Intent data) {
        Bitmap bm = null;
        try {
            Bitmap tempBitmap = (Bitmap) data.getExtras().get("data");
            bm = tempBitmap;
            FileOutputStream out = new FileOutputStream(htcUri.getPath());
            tempBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();
            String bmMessage = "image width:" + tempBitmap.getWidth() + " height:" + tempBitmap.getHeight();
            showAlert(this, "HTC Image info", "Thumbnail saved to:" + htcUri.getPath() + "\nThumbnail attributes:" + bmMessage);
        } catch (NullPointerException ex) {
            showAlert(this, "Info", "this device doesn't support images the same way as an HTC device.\n"+
            "You would have to decode the image from:"+htcUri.getPath());
        } catch (Exception e) {
            showAlert(this, "Error:", "problem setting up the image:" + e.getMessage());
        }
        return bm;
    }


    /* utility method to convert a URI into an actual File/Path */
    public static File convertImageUriToFile(Uri imageUri, Activity activity) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID, MediaStore.Images.ImageColumns.ORIENTATION};
            cursor = activity.managedQuery(imageUri,
                    proj, // Which columns to return
                    null,       // WHERE clause; which rows to return (all rows)
                    null,       // WHERE clause selection arguments (none)
                    null); // Order-by clause (ascending by name)
            int file_ColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int orientation_ColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION);
            if (cursor.moveToFirst()) {
                String orientation = cursor.getString(orientation_ColumnIndex);
                return new File(cursor.getString(file_ColumnIndex));
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


}
