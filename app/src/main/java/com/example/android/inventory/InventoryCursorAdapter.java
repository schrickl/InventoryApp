package com.example.android.inventory;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.inventory.data.InventoryContract;
import com.example.android.inventory.data.InventoryContract.ProductEntry;

/**
 * {@link InventoryCursorAdapter} is an adapter for a list or grid view
 * that uses a {@link Cursor} of product data as its data source. This adapter knows
 * how to create list items for each row of product data in the {@link Cursor}.
 */
public class InventoryCursorAdapter extends CursorAdapter {

    /**
     * Constructs a new {@link InventoryCursorAdapter}.
     *
     * @param context The context
     * @param c       The cursor from which to get the data.
     */
    public InventoryCursorAdapter(Context context, Cursor c) {
        super(context, c, 0 /* flags */);
    }

    /**
     * Makes a new blank list item view. No data is set (or bound) to the views yet.
     *
     * @param context app context
     * @param cursor  The cursor from which to get the data. The cursor is already
     *                moved to the correct position.
     * @param parent  The parent to which the new view is attached to
     * @return the newly created list item view.
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // Inflate a list item view using the layout specified in list_item.xml
        return LayoutInflater.from(context).inflate(R.layout.product_list_item, parent, false);
    }

    /**
     * This method binds the product data (in the current row pointed to by cursor) to the given
     * list item layout. For example, the name for the current product can be set on the name TextView
     * in the list item layout.
     *
     * @param view    Existing view, returned earlier by newView() method
     * @param context app context
     * @param cursor  The cursor from which to get the data. The cursor is already moved to the
     *                correct row.
     */
    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        // Find individual views that we want to modify in the list item layout
        TextView nameTextView = (TextView) view.findViewById(R.id.tv_name_actual);
        final TextView quantityTextView = (TextView) view.findViewById(R.id.tv_in_stock_actual);
        TextView priceTextView = (TextView) view.findViewById(R.id.tv_unit_price_actual);
        Button sellButton = (Button) view.findViewById(R.id.btn_sell);

        // Find the columns of product attributes that we're interested in
        int idColumnIndex = cursor.getColumnIndex(ProductEntry._ID);
        int nameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_NAME);
        int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);
        int priceColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_PRICE);

        // Read the product attributes from the Cursor for the current product
        final int rowId = cursor.getInt(idColumnIndex);
        String productName = cursor.getString(nameColumnIndex);
        final int productQuantity = cursor.getInt(quantityColumnIndex);
        float productPrice = cursor.getFloat(priceColumnIndex);

        // Update the TextViews with the attributes for the current product
        nameTextView.setText(productName);
        quantityTextView.setText(String.valueOf(productQuantity));
        priceTextView.setText("$" + String.format("%.2f", productPrice));

        // Set up a click listener on the Sell button
        sellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int quantity = productQuantity;

                if(quantity == 0) {
                    // Notify the user that the current product is out of stock
                    Toast.makeText(context, R.string.toast_out_of_stock, Toast.LENGTH_LONG).show();
                } else {
                    // Subtract one from the quantity and update the inventory database
                    quantity -= 1;

                    ContentValues values = new ContentValues();
                    values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantity);
                    Uri currentInventoryUri = ContentUris.withAppendedId(InventoryContract.ProductEntry.CONTENT_URI, rowId);

                    int rowsUpdated = context.getContentResolver().update(currentInventoryUri, values,
                            null, null);

                    if (rowsUpdated > 0) {
                        // Update text view if database update is successful
                        quantityTextView.setText(String.valueOf(quantity));
                    }
                }
            }
        });
    }
}

