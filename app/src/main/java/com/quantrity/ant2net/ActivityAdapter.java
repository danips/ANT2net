package com.quantrity.ant2net;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.garmin.fit.Sport;

import java.io.File;
import java.util.ArrayList;

class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
    private final static String TAG = "ActivityAdapter";
    //private static final int RED = Color.parseColor("#C24641");
    private ArrayList<SportActivity> mDataset;
    private Context mContext;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    class ViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        // each data item is just a string in this case
        private SportActivity sa;
        ImageView sportIV;
        TextView dateTV;
        View fillerView;
        ImageView emIV;
        ImageView em2IV;
        FrameLayout emFL;
        ServicesHorizontalScrollView hs;

        ViewHolder(View v) {
            super(v);
            dateTV = v.findViewById(R.id.dateTV);
            sportIV = v.findViewById(R.id.sportIV);
            fillerView = v.findViewById(R.id.fillerView);
            emIV = v.findViewById(R.id.emIV);
            em2IV = v.findViewById(R.id.em2IV);
            emFL = v.findViewById(R.id.emFL);
            hs = v.findViewById(R.id.hview);
            ImageView r = v.findViewById(R.id.next);
            ImageView l = v.findViewById(R.id.previous);
            hs.setArrows(r, l);

            v.setOnCreateContextMenuListener(this);
            OnClickListener ocl = new OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.showContextMenu();
                }
            };
            sportIV.setOnClickListener(ocl);
            dateTV.setOnClickListener(ocl);
            fillerView.setOnClickListener(ocl);
            r.setOnClickListener(ocl);
            l.setOnClickListener(ocl);
        }

        private void uploadAction(SportActivity sa, AsyncUpload.ServicesEnum services) {
            AsyncUpload au = new AsyncUpload((MainActivity)mContext, ActivityAdapter.this, sa, services);
            au.execute();

            synchronized (sa) {
                File file = new File(mContext.getFilesDir() + "/fits", sa.toString());
                if (services == AsyncUpload.ServicesEnum.ALL || services == AsyncUpload.ServicesEnum.EMAIL)
                    sa.setEm(2);
                File file2 = new File(mContext.getFilesDir() + "/fits", sa.toString());
                file.renameTo(file2);
            }
            update(sa);
        }

        @Override
        public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
            contextMenu.setHeaderTitle(R.string.activitylist_contextmenu_select_action);

            MenuItem mi = contextMenu.add(0, view.getId(), 0, R.string.activitylist_contextmenu_delete);
            mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    boolean deleted = SportActivity.delete(sa, mContext);
                    if (deleted) remove(sa);
                    return true;
                }
            });

            int counter = 0, total = 0, counter_ok = 0;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

            String gmail_to = prefs.getString("gmail_to", "");
            if ((!gmail_to.equals(""))) {
                total++;
                if ((sa.getEm() == 0) || sa.getEm() == 3) {
                    counter++;
                    mi = contextMenu.add(0, view.getId(), 0, String.format(mContext.getString(R.string.activitylist_contextmenu_upload_to), mContext.getString(R.string.preferences_email)));
                    mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            uploadAction(sa, AsyncUpload.ServicesEnum.EMAIL);
                            return true;
                        }
                    });
                } else counter_ok++;
            }

            if (counter == total && counter != 0) {
                mi = contextMenu.add(0, view.getId(), 0, R.string.activitylist_contextmenu_upload_to_all);
                mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        uploadAction(sa, AsyncUpload.ServicesEnum.ALL);
                        return true;
                    }
                });
            }

            if (counter_ok != 0) {
                mi = contextMenu.add(0, view.getId(), 0, R.string.activitylist_contextmenu_reset_upload_status);
                mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        synchronized (sa) {
                            File file = new File(mContext.getFilesDir() + "/fits", sa.toString());
                            sa.resetUploadStatus();
                            File file2 = new File(mContext.getFilesDir() + "/fits", sa.toString());

                            file.renameTo(file2);
                        }
                        update(sa);
                        return true;
                    }
                });
            }
        }
    }


    public void add(int position, SportActivity item) {
        mDataset.add(position, item);
        notifyItemInserted(position);
    }

    public void remove(SportActivity item) {
        int position = mDataset.indexOf(item);
        mDataset.remove(position);
        notifyItemRemoved(position);
    }

    public void update(SportActivity item) {
        int position = mDataset.indexOf(item);
        notifyItemChanged(position);
    }

    public SportActivity get(SportActivity item) {
        return mDataset.get(mDataset.indexOf(item));
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    ActivityAdapter(ArrayList<SportActivity> myDataset, Context mContext) {
        mDataset = myDataset;
        this.mContext = mContext;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row, parent, false);
        // set the view's size, margins, paddings and layout parameters
        return new ViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        //Log.v(TAG, "onBindViewHolder");
        final SportActivity item = mDataset.get(position);
        holder.sa = item;

        if (item.getSport() == Sport.CYCLING.getValue()) holder.sportIV.setImageResource(R.mipmap.ic_bike);
        else if (item.getSport() == Sport.RUNNING.getValue()) holder.sportIV.setImageResource(R.mipmap.ic_run);
        else if (item.getSport() == Sport.SWIMMING.getValue()) holder.sportIV.setImageResource(R.mipmap.ic_swim);
        else holder.sportIV.setImageResource(R.mipmap.ic_nosport);

        int hours = (int)item.getDuration() / 3600;
        int minutes = (int)(item.getDuration() % 3600) / 60;
        int seconds = (int)item.getDuration() % 60;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String units = preferences.getString("units", "0");
        holder.dateTV.setText(DateUtils.formatDateTime(mContext, item.getDate(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME)
        + "\n" + String.format("%d:%02d:%02d", hours, minutes, seconds) + "   "
                + ((units.equals("1"))
                    ? (String.format((item.getSport() == Sport.SWIMMING.getValue()) ? "%.3f" : "%.1f", item.getDistance() * 0.621371192f) + " mi")
                    : (String.format((item.getSport() == Sport.SWIMMING.getValue()) ? "%.3f" : "%.1f", item.getDistance()) + " km"))
                + ((item.getTrainingEffect() != -1f) ? "   TE " + String.format("%.1f", item.getTrainingEffect()) :""));

        String gmail_to = preferences.getString("gmail_to", "");
        if (!gmail_to.equals("")) {
            holder.emFL.setVisibility(View.VISIBLE);
            if (item.getEm() == 1) {
                holder.emFL.setOnClickListener(null);
                updateIV(holder.emIV, R.mipmap.ic_gmail, false);
                holder.em2IV.setImageResource(R.mipmap.ic_ok);
                holder.em2IV.setVisibility(View.VISIBLE);
            } else if ((item.getEm() == 0) || (item.getEm() == 3)) {
                updateIV(holder.emIV, R.mipmap.ic_gmail_no, false);
                holder.em2IV.setImageResource(R.mipmap.ic_nok);
                holder.em2IV.setVisibility(View.VISIBLE);
                holder.emFL.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AsyncUpload au = new AsyncUpload((MainActivity)mContext, ActivityAdapter.this, item, AsyncUpload.ServicesEnum.EMAIL);
                        au.execute();

                        synchronized (item) {
                            File file = new File(mContext.getFilesDir() + "/fits", item.toString());
                            item.setEm(2);
                            File file2 = new File(mContext.getFilesDir() + "/fits", item.toString());
                            file.renameTo(file2);
                        }
                        update(item);
                    }
                });
            } else {
                holder.emFL.setOnClickListener(null);
                updateIV(holder.emIV, R.mipmap.ic_gmail, true);
                holder.em2IV.setVisibility(View.INVISIBLE);
            }
        } else {
            //updateIV(holder.emIV, false, false);
            holder.emFL.setVisibility(View.GONE);
        }
    }


    private static final float ROTATE_FROM = 0.0f;
    private static final float ROTATE_TO = 360.0f;

    private void updateIV(ImageView iv, int  resId, boolean animate) {
        iv.setImageResource(resId);
        if (!animate) iv.clearAnimation();
        else {
            RotateAnimation r = new RotateAnimation(ROTATE_FROM, ROTATE_TO, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            r.setDuration(5000);
            r.setRepeatCount(Animation.INFINITE);
            iv.startAnimation(r);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    void deleteAllActivities() {
        File dir = new File(mContext.getFilesDir() + "/fits");
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            if (children != null) {
                for (String aChildren : children) {
                    File tmp = new File(dir, aChildren);
                    if (tmp.exists()) tmp.delete();
                }
            }
        }
        mDataset.clear();
        notifyDataSetChanged();
    }

}
