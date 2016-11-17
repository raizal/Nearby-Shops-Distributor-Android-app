package org.localareadelivery.distributorapp.DeliveryGuyInventory.PendingHandover;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.localareadelivery.distributorapp.ApplicationState.ApplicationState;
import org.localareadelivery.distributorapp.DaggerComponentBuilder;
import org.localareadelivery.distributorapp.HomeDeliveryInventory.Interface.NotifyTitleChanged;
import org.localareadelivery.distributorapp.Model.Order;
import org.localareadelivery.distributorapp.Model.Shop;
import org.localareadelivery.distributorapp.ModelEndpoints.OrderEndPoint;
import org.localareadelivery.distributorapp.Model.DeliveryGuySelf;
import org.localareadelivery.distributorapp.ModelStatusCodes.OrderStatusHomeDelivery;
import org.localareadelivery.distributorapp.R;
import org.localareadelivery.distributorapp.RetrofitRESTContract.OrderService;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import icepick.State;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by sumeet on 13/6/16.
 */


public class PendingHandoverFragment extends Fragment
        implements SwipeRefreshLayout.OnRefreshListener,AdapterPendingHandover.NotifyCancelHandover {


    @Inject
    OrderService orderService;

    RecyclerView recyclerView;
    AdapterPendingHandover adapter;

    public List<Order> dataset = new ArrayList<>();

    GridLayoutManager layoutManager;
    SwipeRefreshLayout swipeContainer;



//    NotificationReceiver notificationReceiver;

    DeliveryGuySelf deliveryGuySelf;



    final private int limit = 5;
    @State int offset = 0;
    @State int item_count = 0;
    boolean isDestroyed;




    public PendingHandoverFragment() {

        DaggerComponentBuilder.getInstance()
                .getNetComponent()
                .Inject(this);

    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static PendingHandoverFragment newInstance() {
        PendingHandoverFragment fragment = new PendingHandoverFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home_delivery_pending_accept_vd, container, false);


        setRetainInstance(true);

        recyclerView = (RecyclerView) rootView.findViewById(R.id.recyclerView);
        swipeContainer = (SwipeRefreshLayout)rootView.findViewById(R.id.swipeContainer);




        if(savedInstanceState!=null)
        {
            // restore instance state
            deliveryGuySelf = savedInstanceState.getParcelable("savedVehicle");
        }
        else
        {
            makeRefreshNetworkCall();
        }



        setupRecyclerView();
        setupSwipeContainer();

        return rootView;
    }


    void setupSwipeContainer()
    {
        if(swipeContainer!=null) {

            swipeContainer.setOnRefreshListener(this);
            swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light);
        }

    }


    void setupRecyclerView()
    {

        adapter = new AdapterPendingHandover(dataset,this);

        recyclerView.setAdapter(adapter);

        layoutManager = new GridLayoutManager(getActivity(),1);
        recyclerView.setLayoutManager(layoutManager);

        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

//        layoutManager.setSpanCount(metrics.widthPixels/400);



        int spanCount = (int) (metrics.widthPixels/(230 * metrics.density));

        if(spanCount==0){
            spanCount = 1;
        }

        layoutManager.setSpanCount(spanCount);


        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if(layoutManager.findLastVisibleItemPosition()==dataset.size()-1)
                {
                    // trigger fetch next page

                    if(layoutManager.findLastVisibleItemPosition() == previous_position)
                    {
                        return;
                    }


                    if((offset+limit)<=item_count)
                    {
                        offset = offset + limit;


                        swipeContainer.post(new Runnable() {
                            @Override
                            public void run() {

                                swipeContainer.setRefreshing(true);

                                makeNetworkCall(false);
                            }
                        });

                    }

                    previous_position = layoutManager.findLastVisibleItemPosition();
                }
            }
        });
    }


    int previous_position = -1;

    @Override
    public void onRefresh() {

        offset = 0;
        makeNetworkCall(true);
    }


    void makeRefreshNetworkCall()
    {

        swipeContainer.post(new Runnable() {
            @Override
            public void run() {

                swipeContainer.setRefreshing(true);
                onRefresh();
            }
        });
    }



    @Override
    public void onResume() {
        super.onResume();
        notifyTitleChanged();
    }


    void makeNetworkCall(final boolean clearDataset)
    {

        if(deliveryGuySelf ==null)
        {
            return;
        }

        Shop currentShop = ApplicationState.getInstance().getCurrentShop();

            Call<OrderEndPoint> call = orderService
                    .getOrders(null, currentShop.getShopID(),false,
                            OrderStatusHomeDelivery.PENDING_HANDOVER,
                            null, deliveryGuySelf.getDeliveryGuyID(),null,null,true,true,
                            null,limit,offset,null);


            call.enqueue(new Callback<OrderEndPoint>() {
                @Override
                public void onResponse(Call<OrderEndPoint> call, Response<OrderEndPoint> response) {

                    if(isDestroyed)
                    {
                        return;
                    }

                    if(response.body()!= null)
                    {
                        item_count = response.body().getItemCount();

                        if(clearDataset)
                        {
                            dataset.clear();
                        }

                        dataset.addAll(response.body().getResults());
                        adapter.notifyDataSetChanged();
                        notifyTitleChanged();

                    }

                    swipeContainer.setRefreshing(false);

                }

                @Override
                public void onFailure(Call<OrderEndPoint> call, Throwable t) {
                    if(isDestroyed)
                    {
                        return;
                    }

                    showToastMessage("Network Request failed !");
                    swipeContainer.setRefreshing(false);
                }
            });

    }



    void showToastMessage(String message)
    {
        if(getActivity()!=null)
        {
            Toast.makeText(getActivity(),message,Toast.LENGTH_SHORT).show();
        }

    }



    @Override
    public void notifyCancelHandover(Order order) {


        order.setStatusHomeDelivery(OrderStatusHomeDelivery.ORDER_PACKED);
        order.setDeliveryVehicleSelfID(null);

        Call<ResponseBody> call = orderService.putOrder(order.getOrderID(),order);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                if(response.code()==200)
                {
                    showToastMessage("Handover cancelled !");
                    makeRefreshNetworkCall();
                }

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

                showToastMessage("Network Request Failed. Try again !");

            }
        });
    }





    @Override
    public void notifyCancelOrder(final Order order) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle("Confirm Cancel Order !")
                .setMessage("Are you sure you want to cancel this order !")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        cancelOrder(order);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        showToastMessage(" Not Cancelled !");
                    }
                })
                .show();
    }




    private void cancelOrder(Order order) {

        Call<ResponseBody> call = orderService.cancelOrderByShop(order.getOrderID());

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                if(response.code() == 200 )
                {
                    showToastMessage("Successful");
                    makeRefreshNetworkCall();
                }
                else if(response.code() == 304)
                {
                    showToastMessage("Not Cancelled !");
                }
                else
                {
                    showToastMessage("Server Error");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

                showToastMessage("Network Request Failed. Check your internet connection !");
            }
        });

    }


    public DeliveryGuySelf getDeliveryGuySelf() {
        return deliveryGuySelf;
    }

    public void setDeliveryGuySelf(DeliveryGuySelf deliveryGuySelf) {
        this.deliveryGuySelf = deliveryGuySelf;
    }

    /*public interface NotificationReceiver
    {
        void notifyPendingAcceptChanged();
    }
    */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("savedVehicle", deliveryGuySelf);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }



    void notifyTitleChanged()
    {

        if(getActivity() instanceof NotifyTitleChanged)
        {
            ((NotifyTitleChanged)getActivity())
                    .NotifyTitleChanged(
                            "Pending Handover ( " + String.valueOf(dataset.size())
                                    + "/" + String.valueOf(item_count) + " )",0);


        }
    }


}
