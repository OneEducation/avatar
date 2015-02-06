package com.xoid.xodatainterface;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

public class XoDataProvider {
  public interface DataCallback {
    void onDataAvailable(String data);
    void onError(String description);
  }

  private final XoDataServiceConnection xoDataServiceConnection;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  
  public XoDataProvider(Context context) {
    xoDataServiceConnection = new XoDataServiceConnection(context.getApplicationContext());
  }
  
  public void launchLoginActivity(Context context) {
    Intent intent = new Intent("com.xosignin.xodataservice.LAUNCH_LOGIN");
    context.startActivity(intent);
  }
  
  public void requestStudentData(final DataCallback callback) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          String data = xoDataServiceConnection.getDataJson();
          if (data != null) {
            callback.onDataAvailable(data);
          } else {
            callback.onError("No logged in user, data unavailable");
          }
        } catch (RemoteException e) {
          callback.onError(e.getMessage());
        } catch (InterruptedException e) {
          callback.onError(e.getMessage());
        }
      }
    });
  }
}
