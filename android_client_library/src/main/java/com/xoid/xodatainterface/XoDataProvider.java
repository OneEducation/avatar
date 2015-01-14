package com.xoid.xodatainterface;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

public class XoDataProvider {
  public interface StudentDataCallback {
    void onStudentDataAvailable(String studentData);
    void onError(String description);
  }
  
  public interface IsStudentLoggedInCallback {
    void onIsStudentLoggedInAvailable(boolean isLoggedIn);
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
  
  public void requestIsStudentLoggedIn(final IsStudentLoggedInCallback callback) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          boolean loggedIn = xoDataServiceConnection.isStudentLoggedIn();
          callback.onIsStudentLoggedInAvailable(loggedIn);
        } catch (RemoteException e) {
          callback.onError(e.getMessage());
        } catch (InterruptedException e) {
          callback.onError(e.getMessage());
        }
      }
    });
  }
  
  public void requestStudentData(final StudentDataCallback callback) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          String studentData = xoDataServiceConnection.getStudentDataJson();
          if (studentData != null) {
            callback.onStudentDataAvailable(studentData);
          } else {
            callback.onError("Student not logged in, data unavailable");
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
