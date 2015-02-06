package com.xosignin.service.aidl;

interface IXoServiceInterface {

    /** Returns null if no user is logged in, otherwise returns either the
     *  student json, or the teacher json
     */
    String getDataJson();
}