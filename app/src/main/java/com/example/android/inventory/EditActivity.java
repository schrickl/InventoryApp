package com.example.android.inventory;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.inventory.data.InventoryContract.ProductEntry;
import com.example.android.inventory.data.InventoryProvider;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Allows user to create a new product or activity_edit an existing one.
 */
public class EditActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    // Tag for log messages
    private static final String LOG_TAG = EditActivity.class.getSimpleName();

    // Identifier for the product data loader
    private static final int EXISTING_PRODUCT_LOADER = 0;

    // Request code for select photo intent
    private static final int REQUEST_IMAGE_GET = 11;

    // Content URI for the existing product (null if it's a new product)
    private Uri mCurrentProductUri;

    // EditText field to enter the product's name
    private EditText mNameEditText;

    // EditText field to enter the product's price
    private EditText mPriceEditText;

    // TextView field to enter the product's in-stock quantity
    private TextView mQuantityTextView;

    // EditText field to enter the supplier's email address
    private EditText mEmailEditText;

    // Button field to restock the current product (once a restock request has been sent)
    private Button mRestockButton;

    // ImageView field to hold a photo of the product, if available
    private ImageView mPhotoImageView;

    // Boolean flag that keeps track of whether the product has been edited (true) or not (false)
    private boolean mProductHasChanged = false;

    // Photo Uri string
    private Uri mUri = null;

    // Global intent for choosing image
    private Intent i;

    /**
     * OnTouchListener that listens for any user touches on a View, implying that they are modifying
     * the view, and we change the mProductHasChanged boolean to true.
     */
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mProductHasChanged = true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        // Examine the intent that was used to launch this activity,
        // in order to figure out if we're creating a new product or editing an existing one.
        Intent intent = getIntent();
        mCurrentProductUri = intent.getData();

        // If the intent DOES NOT contain a product content URI, then we know that we are
        // creating a new product.
        if (mCurrentProductUri == null) {
            // This is a new product, so change the app bar to say "Add a Product"
            setTitle(getString(R.string.title_new_product));

            // Invalidate the options menu, so the "Delete" menu option can be hidden.
            // (It doesn't make sense to delete a product that hasn't been created yet.)
            invalidateOptionsMenu();
        } else {
            // Otherwise this is an existing product, so change app bar to say "Edit Product"
            setTitle(getString(R.string.title_edit_product));

            // Initialize a loader to read the product data from the database
            // and display the current values in the editor
            getLoaderManager().initLoader(EXISTING_PRODUCT_LOADER, null, this);
        }

        // Find all relevant views that we will need to read user input from
        mNameEditText = (EditText) findViewById(R.id.et_product_name);
        mPriceEditText = (EditText) findViewById(R.id.et_product_price);
        mQuantityTextView = (TextView) findViewById(R.id.et_instock_quantity);
        mEmailEditText = (EditText) findViewById(R.id.et_email);
        mPhotoImageView = (ImageView) findViewById(R.id.iv_product_photo);
        mPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (Build.VERSION.SDK_INT < 19) {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                } else {
                    i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                }

                i.setType("image/*");
                if (i.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(i, REQUEST_IMAGE_GET);
                }
            }
        });
        Button mIncreaseButton = (Button) findViewById(R.id.btn_plus);
        mIncreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String quantityString = mQuantityTextView.getText().toString().trim();
                int quantityInt = Integer.parseInt(quantityString);
                quantityInt += 1;
                mQuantityTextView.setText(String.valueOf(quantityInt));
            }
        });
        Button mDecreaseButton = (Button) findViewById(R.id.btn_minus);
        mDecreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String quantityString = mQuantityTextView.getText().toString().trim();
                int quantityInt = Integer.parseInt(quantityString);
                if(quantityInt > 0) {
                    quantityInt -= 1;
                    mQuantityTextView.setText(String.valueOf(quantityInt));
                } else {
                    Toast.makeText(EditActivity.this, R.string.toast_negative_quantity, Toast.LENGTH_LONG).show();
                }
            }
        });
        mRestockButton = (Button) findViewById(R.id.btn_restock);
        // Set the Restock Product button disabled until an order has been placed with the supplier
        mRestockButton.setEnabled(false);
        mRestockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String quantityString = mQuantityTextView.getText().toString().trim();
                int quantityInt = Integer.parseInt(quantityString);
                quantityInt += 25;
                mQuantityTextView.setText(String.valueOf(quantityInt));
                mRestockButton.setEnabled(false);
            }
        });
        Button mPlaceOrderButton = (Button) findViewById(R.id.btn_order);
        mPlaceOrderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendEmail();
                // Request sent, enable the Restock Product button
                mRestockButton.setEnabled(true);
            }
        });
        Button mDeleteProductButton = (Button) findViewById(R.id.btn_delete);
        mDeleteProductButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Pop up confirmation dialog for deletion
                showDeleteConfirmationDialog();
            }
        });

        // Setup OnTouchListeners on all the input fields, so we can determine if the user
        // has touched or modified them. This will let us know if there are unsaved changes
        // or not, if the user tries to leave the editor without saving.
        mPhotoImageView.setOnTouchListener(mTouchListener);
        mNameEditText.setOnTouchListener(mTouchListener);
        mPriceEditText.setOnTouchListener(mTouchListener);
        mQuantityTextView.setOnTouchListener(mTouchListener);
        mIncreaseButton.setOnTouchListener(mTouchListener);
        mDecreaseButton.setOnTouchListener(mTouchListener);
        mPlaceOrderButton.setOnTouchListener(mTouchListener);
        mDeleteProductButton.setOnTouchListener(mTouchListener);
        mRestockButton.setOnTouchListener(mTouchListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK) {
            if (data != null) {
                mUri = data.getData();
                Bitmap bm = getBitmapFromUri(mUri);
                if (bm != null) {
                    mPhotoImageView.setImageBitmap(bm);
                } else {
                    mPhotoImageView.setImageResource(R.drawable.ic_add_image);
                }
            } else {
                Toast.makeText(this, getString(R.string.photo_load_error), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Get user input from editor and save product into database.
     */
    private void saveProduct() {
        // Read from input fields
        // Use trim to eliminate leading or trailing white space
        String nameString = mNameEditText.getText().toString().trim();
        String priceString = mPriceEditText.getText().toString().trim();
        String quantityString = mQuantityTextView.getText().toString().trim();
        String emailString = mEmailEditText.getText().toString().trim();
        String photoString = getPhotoUri();

        // Set the photoString from the Uri if not null
        if (mUri != null) {
            photoString = mUri.toString();
        }

        // Check if this is supposed to be a new product
        // and check if all the fields in the editor are blank
        if (mCurrentProductUri == null &&
                TextUtils.isEmpty(nameString) && TextUtils.isEmpty(priceString) &&
                TextUtils.isEmpty(emailString) && TextUtils.isEmpty(photoString)) {
            // Since no fields were modified, we can return without creating a new product.
            // No need to create ContentValues and no need to do any ContentProvider operations.
            Toast.makeText(this, getString(R.string.error_nothing_to_save), Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure there is a product name prior to saving
        if (TextUtils.isEmpty(nameString)) {
            Toast.makeText(this, getString(R.string.error_no_product_name), Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure there is a product price prior to saving
        if (TextUtils.isEmpty(priceString)) {
            Toast.makeText(this, getString(R.string.error_no_product_price), Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure there is a supplier email prior to saving
        if (TextUtils.isEmpty(emailString)) {
            Toast.makeText(this, getString(R.string.error_no_supplier_email), Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure the supplier email is valid prior to saving
        if (!InventoryProvider.isValidEmail(emailString)) {
            Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a ContentValues object where column names are the keys,
        // and product attributes from the editor are the values.
        ContentValues values = new ContentValues();
        values.put(ProductEntry.COLUMN_PRODUCT_NAME, nameString);
        values.put(ProductEntry.COLUMN_PRODUCT_PRICE, priceString);
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantityString);
        values.put(ProductEntry.COLUMN_PRODUCT_SUPPLIER, emailString);
        values.put(ProductEntry.COLUMN_PRODUCT_PHOTO, photoString);

        // Determine if this is a new or existing product by checking if mCurrentProductUri is null or not
        if (mCurrentProductUri == null) {
            // This is a NEW product, so insert a new product into the provider,
            // returning the content URI for the new product.
            Uri newUri = getContentResolver().insert(ProductEntry.CONTENT_URI, values);

            // Show a toast message depending on whether or not the insertion was successful.
            if (newUri == null) {
                // If the new content URI is null, then there was an error with insertion.
                Toast.makeText(this, getString(R.string.insert_product_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                // Otherwise, the insertion was successful and we can display a toast.
                Toast.makeText(this, getString(R.string.insert_product_successful),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            if(mProductHasChanged) {
                // Otherwise this is an EXISTING product, so update the product with content URI: mCurrentProductUri
                // and pass in the new ContentValues. Pass in null for the selection and selection args
                // because mCurrentProductUri will already identify the correct row in the database that
                // we want to modify.
                int rowsAffected = getContentResolver().update(mCurrentProductUri, values, null, null);

                // Show a toast message depending on whether or not the update was successful.
                if (rowsAffected == 0) {
                    // If no rows were affected, then there was an error with the update.
                    Toast.makeText(this, getString(R.string.update_product_failed),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Otherwise, the update was successful and we can display a toast.
                    Toast.makeText(this, getString(R.string.update_product_successful),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        // Exit the activity
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu options from the res/menu/menu_editor.xml file.
        // This adds menu items to the app bar.
        getMenuInflater().inflate(R.menu.edit_activity_menu, menu);
        return true;
    }

    /**
     * This method is called after invalidateOptionsMenu(), so that the
     * menu can be updated (some menu items can be hidden or made visible).
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // If this is a new product, hide the "Delete" menu item.
        if (mCurrentProductUri == null) {
            MenuItem menuItem = menu.findItem(R.id.action_delete);
            menuItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // User clicked on a menu option in the app bar overflow menu
        switch (item.getItemId()) {
            // Respond to a click on the "Save" menu option
            case R.id.action_save:
                // Save product to database
                saveProduct();
                return true;
            // Respond to a click on the "Delete" menu option
            case R.id.action_delete:
                // Pop up confirmation dialog for deletion
                showDeleteConfirmationDialog();
                return true;
            // Respond to a click on the "Up" arrow button in the app bar
            case android.R.id.home:
                // If the product hasn't changed, continue with navigating up to parent activity
                // which is the {@link CatalogActivity}.
                if (!mProductHasChanged) {
                    NavUtils.navigateUpFromSameTask(EditActivity.this);
                    return true;
                }

                // Otherwise if there are unsaved changes, setup a dialog to warn the user.
                // Create a click listener to handle the user confirming that
                // changes should be discarded.
                DialogInterface.OnClickListener discardButtonClickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // User clicked "Discard" button, navigate to parent activity.
                                NavUtils.navigateUpFromSameTask(EditActivity.this);
                            }
                        };

                // Show a dialog that notifies the user they have unsaved changes
                showUnsavedChangesDialog(discardButtonClickListener);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the back button is pressed.
     */
    @Override
    public void onBackPressed() {
        // If the product hasn't changed, continue with handling back button press
        if (!mProductHasChanged) {
            super.onBackPressed();
            return;
        }

        // Otherwise if there are unsaved changes, setup a dialog to warn the user.
        // Create a click listener to handle the user confirming that changes should be discarded.
        DialogInterface.OnClickListener discardButtonClickListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // User clicked "Discard" button, close the current activity.
                        finish();
                    }
                };

        // Show dialog that there are unsaved changes
        showUnsavedChangesDialog(discardButtonClickListener);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Since the editor shows all product attributes, define a projection that contains
        // all columns from the product table
        String[] projection = {
                ProductEntry._ID,
                ProductEntry.COLUMN_PRODUCT_NAME,
                ProductEntry.COLUMN_PRODUCT_PRICE,
                ProductEntry.COLUMN_PRODUCT_QUANTITY,
                ProductEntry.COLUMN_PRODUCT_SUPPLIER,
                ProductEntry.COLUMN_PRODUCT_PHOTO};

        // This loader will execute the ContentProvider's query method on a background thread
        return new CursorLoader(this,   // Parent activity context
                mCurrentProductUri,     // Query the content URI for the current product
                projection,             // Columns to include in the resulting Cursor
                null,                   // No selection clause
                null,                   // No selection arguments
                null);                  // Default sort order
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Bail early if the cursor is null or there is less than 1 row in the cursor
        if (cursor == null || cursor.getCount() < 1) {
            return;
        }

        // Proceed with moving to the first row of the cursor and reading data from it
        // (This should be the only row in the cursor)
        if (cursor.moveToFirst()) {
            // Find the columns of product attributes that we're interested in
            int nameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_NAME);
            int priceColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_PRICE);
            int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);
            int emailColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_SUPPLIER);
            int photoColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_PHOTO);

            // Extract out the value from the Cursor for the given column index
            String name = cursor.getString(nameColumnIndex);
            String price = cursor.getString(priceColumnIndex);
            int quantity = cursor.getInt(quantityColumnIndex);
            String email = cursor.getString(emailColumnIndex);
            String photo = cursor.getString(photoColumnIndex);

            // Update the views on the screen with the values from the database
            mNameEditText.setText(name);
            mPriceEditText.setText(price);
            mQuantityTextView.setText(Integer.toString(quantity));
            mEmailEditText.setText(email);

            if (!TextUtils.isEmpty(photo)) {
                mPhotoImageView.setImageURI(Uri.parse(photo));
            } else {
                mPhotoImageView.setImageResource(R.drawable.ic_add_image);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // If the loader is invalidated, clear out all the data from the input fields.
        mNameEditText.setText("");
        mPriceEditText.setText("");
        mQuantityTextView.setText("");
        mEmailEditText.setText("");
        mPhotoImageView.setImageResource(R.drawable.ic_add_image);
        mRestockButton.setEnabled(false);
    }

    /**
     * Show a dialog that warns the user there are unsaved changes that will be lost
     * if they continue leaving the editor.
     *
     * @param discardButtonClickListener is the click listener for what to do when
     *                                   the user confirms they want to discard their changes
     */
    private void showUnsavedChangesDialog(
            DialogInterface.OnClickListener discardButtonClickListener) {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the positive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_msg);
        builder.setPositiveButton(R.string.discard, discardButtonClickListener);
        builder.setNegativeButton(R.string.keep_editing, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Keep editing" button, so dismiss the dialog
                // and continue editing the product.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Prompt the user to confirm that they want to delete this product.
     */
    private void showDeleteConfirmationDialog() {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the positive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_dialog_msg);
        builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Delete" button, so delete the product.
                deleteProduct();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                // and continue editing the product.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Perform the deletion of the product in the database.
     */
    private void deleteProduct() {
        // Only perform the delete if this is an existing product.
        if (mCurrentProductUri != null) {
            // Call the ContentResolver to delete the product at the given content URI.
            // Pass in null for the selection and selection args because the mCurrentProductUri
            // content URI already identifies the product that we want.
            int rowsDeleted = getContentResolver().delete(mCurrentProductUri, null, null);

            // Show a toast message depending on whether or not the delete was successful.
            if (rowsDeleted == 0) {
                // If no rows were deleted, then there was an error with the delete.
                Toast.makeText(this, getString(R.string.delete_product_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                // Otherwise, the delete was successful and we can display a toast.
                Toast.makeText(this, getString(R.string.delete_product_successful),
                        Toast.LENGTH_SHORT).show();
            }
        }

        // Close the activity
        finish();
    }

    /**
     * Sends an email to the supplier
     */
    private void sendEmail() {
        // Set up email parameters
        String to[] = new String[]{mEmailEditText.getText().toString().trim()};
        String subject = getString(R.string.email_extra_subject);
        String message = getString(R.string.email_extra_text) + " " +
                mNameEditText.getText().toString().trim() + "." + getString(R.string.email_thanks);

        // Set up email Intent
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, message);
        // Only start the activity if an app exists that can accept the intent
        if (emailIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(emailIntent);
        }
    }

    /**
     * Utility to convert a uri into a bitmap
     * @param uri uri of the photo to convert
     * @return a bitmap of the photo or null if an error occurs
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (Exception e) {
            Log.e(LOG_TAG, getString(R.string.photo_load_error));
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, getString(R.string.photo_parcel_error));
                e.printStackTrace();
            }
        }
    }

    /**
     * Utility method to get the photo uri from the database
     * @return the uri from the database
     */
    private String getPhotoUri() {
        String uri = null;

        if (mCurrentProductUri != null) {
            // Query the database for the photo uri at ProductEntry.COLUMN_PRODUCT_PHOTO
            Cursor c = getContentResolver().query(mCurrentProductUri,
                    new String[]{ProductEntry.COLUMN_PRODUCT_PHOTO}, null, null, null);

            // Proceed with moving to the first row of the cursor and reading data from it
            // (This should be the only row in the cursor)
            if (c.moveToFirst()) {
                // Find the columns of product attributes that we're interested in
                int photoColumnIndex = c.getColumnIndex(ProductEntry.COLUMN_PRODUCT_PHOTO);

                // Extract out the value from the Cursor for the given column index
                uri = c.getString(photoColumnIndex);
            }
        }
        return uri;
    }
}
