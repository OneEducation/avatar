package com.xoid.xodatainterface;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.xosignin.service.aidl.IXoServiceInterface;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class XoDataServiceConnection {
  private static final String TAG = "XoDataServiceConnection";
  private static final String DATA_ACTION = "com.xosignin.xodataservice.GET_DATA";
  
  private ReadWriteLock serviceConnectionLock = new ReentrantReadWriteLock();
  private CountDownLatch serviceConnectionLatch = new CountDownLatch(1);
  private IXoServiceInterface remoteService = null;

  private ServiceConnection connection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      Log.d(TAG, "onServiceConnected");
      serviceConnectionLock.writeLock().lock();
      remoteService = IXoServiceInterface.Stub.asInterface(service);
      serviceConnectionLock.writeLock().unlock();
      serviceConnectionLatch.countDown();
    }

    public void onServiceDisconnected(ComponentName className) {
      Log.d(TAG, "onServiceDisconnected");
      if (remoteService != null) {
        serviceConnectionLock.writeLock().lock();
        
        remoteService = null;
        serviceConnectionLatch = new CountDownLatch(1);
        
        serviceConnectionLock.writeLock().unlock();
      }
    }
  };
  
  private final Context context;
  
  public XoDataServiceConnection(Context context) {
    this.context = context;
    ensureBoundToService();
  }

  // Can block due to potentially needing to spin up the Service.
  public String getDataJson() throws RemoteException, InterruptedException {
    ensureBoundToService();

    try {
      waitUntilConnected();
      return remoteService.getDataJson();
    } finally {
      serviceConnectionLock.readLock().unlock();
    }
  }
  
  private void ensureBoundToService() {
    serviceConnectionLock.readLock().lock();
    if (remoteService == null) {
      Intent intent = new Intent();
      intent.setComponent(new ComponentName("com.xosignin", "com.xosignin.service.XoDataService"));
      context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }
    serviceConnectionLock.readLock().unlock();
  }
  
  private void waitUntilConnected() throws InterruptedException {
    serviceConnectionLock.readLock().lock();
    if (remoteService == null) {
      serviceConnectionLock.readLock().unlock();
      serviceConnectionLatch.await();
      waitUntilConnected();
    }
  }

}
