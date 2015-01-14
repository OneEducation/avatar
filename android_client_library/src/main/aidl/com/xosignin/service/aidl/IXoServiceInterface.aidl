package com.xosignin.service.aidl;

// Declare any non-default types here with import statements

/** Example service interface */
interface IXoServiceInterface {

    boolean isStudentLoggedIn();
    
    String getStudentDataJson();

}