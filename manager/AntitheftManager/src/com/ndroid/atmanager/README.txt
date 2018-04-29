	Frameworks/base/core/java/android/antitheft/
		- created AntiTheftManager
		- created AntiTheftClient

	Frameworks/base/Android.mk
		- added core/java/android/antitheft/IAntiTheftService.aidl \


	Frameworks/base/services/java/com/android/server/SystemServer.java
		- add AntiTheftService to ServiceManager.
	
        // Add AntiTheftService to ServiceManager.
        try {
            Slog.i(TAG, "Added AT_Service");
            ServiceManager.addService(ANTI_THEFT_SERVICE_CLASS, new AntiTheftService(context));
        } catch (Throwable e) {
            Slog.e(TAG, "Failure starting AntiTheftService", e);
        }

    system/sepolicy/service.te
        - add type antitheft_service, system_api_service, system_server_service, service_manager_type;

    system/sepolicy/service_contexts
        - antitheft                                 u:object_r:antitheft_service:s0


    /build/target/product/core_minimal.mk
    - add jar PRODUCT_BOOT_JARS - add ndroid-manager