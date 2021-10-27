
package com.reactnativepurchasely

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import io.purchasely.billing.Store
import io.purchasely.ext.*
import io.purchasely.models.PLYPlan
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class PurchaselyModule internal constructor(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {

  private val eventListener: EventListener = object: EventListener {
    override fun onEvent(event: PLYEvent) {
      if (event.properties != null) {
        val map = mapOf(
          Pair("name", event.name),
          Pair("properties", event.properties!!.toMap())
        )
        sendEvent(reactApplicationContext, "PURCHASELY_EVENTS", Arguments.makeNativeMap(map))
      } else {
        val map = mapOf(Pair("name", event.name))
        sendEvent(reactApplicationContext, "PURCHASELY_EVENTS", Arguments.makeNativeMap(map))
      }
    }
  }

  private val purchaseListener: PurchaseListener = object: PurchaseListener {
    override fun onPurchaseStateChanged(state: State) {
      if(state is State.PurchaseComplete || state is State.RestorationComplete) {
        sendEvent(reactApplicationContext, "PURCHASE_LISTENER", null)
      }
    }
  }

  override fun getName(): String {
    return "Purchasely"
  }

  override fun getConstants(): Map<String, Any> {
    val constants: MutableMap<String, Any> = HashMap()
    constants["logLevelDebug"] = LogLevel.DEBUG.ordinal
    constants["logLevelWarn"] = LogLevel.WARN.ordinal
    constants["logLevelInfo"] = LogLevel.INFO.ordinal
    constants["logLevelError"] = LogLevel.ERROR.ordinal
    constants["productResultPurchased"] = PLYProductViewResult.PURCHASED.ordinal
    constants["productResultCancelled"] = PLYProductViewResult.CANCELLED.ordinal
    constants["productResultRestored"] = PLYProductViewResult.RESTORED.ordinal
    constants["amplitudeSessionId"] = Attribute.AMPLITUDE_SESSION_ID.ordinal
    constants["firebaseAppInstanceId"] = Attribute.FIREBASE_APP_INSTANCE_ID.ordinal
    constants["airshipChannelId"] = Attribute.AIRSHIP_CHANNEL_ID.ordinal
    constants["sourceAppStore"] = StoreType.APPLE_APP_STORE.ordinal
    constants["sourcePlayStore"] = StoreType.GOOGLE_PLAY_STORE.ordinal
    constants["sourceHuaweiAppGallery"] = StoreType.HUAWEI_APP_GALLERY.ordinal
    constants["sourceAmazonAppstore"] = StoreType.AMAZON_APP_STORE.ordinal
    constants["sourceAmazonAppstore"] = StoreType.AMAZON_APP_STORE.ordinal
    constants["sourceAmazonAppstore"] = StoreType.NONE.ordinal
    constants["consumable"] = DistributionType.CONSUMABLE.ordinal
    constants["nonConsumable"] = DistributionType.NON_CONSUMABLE.ordinal
    constants["autoRenewingSubscription"] = DistributionType.RENEWING_SUBSCRIPTION.ordinal
    constants["nonRenewingSubscription"] = DistributionType.NON_RENEWING_SUBSCRIPTION.ordinal
    constants["unknown"] = DistributionType.UNKNOWN.ordinal
    return constants
  }

  private fun getStoresInstances(stores: java.util.ArrayList<Any>): ArrayList<Store> {
    val result = ArrayList<Store>()
    if (stores.contains("Google")
      && Package.getPackage("io.purchasely.google") != null) {
      try {
        result.add(Class.forName("io.purchasely.google.GoogleStore").newInstance() as Store)
      } catch (e: Exception) {
        Log.e("Purchasely", "Google Store not found :" + e.message, e)
      }
    }
    if (stores.contains("Huawei")
      && Package.getPackage("io.purchasely.huawei") != null) {
      try {
        result.add(Class.forName("io.purchasely.huawei.HuaweiStore").newInstance() as Store)
      } catch (e: Exception) {
        Log.e("Purchasely", e.message, e)
      }
    }
    if (stores.contains("Amazon")
      && Package.getPackage("io.purchasely.amazon") != null) {
      try {
        result.add(Class.forName("io.purchasely.amazon.AmazonStore").newInstance() as Store)
      } catch (e: Exception) {
        Log.e("Purchasely", e.message, e)
      }
    }
    return result
  }

  @ReactMethod
  fun startWithAPIKey(apiKey: String,
                      stores: ReadableArray,
                      userId: String?,
                      logLevel: Int,
                      observerMode: Boolean,
                      promise: Promise) {
    val storesInstances = getStoresInstances(stores.toArrayList())

    Purchasely.Builder(reactApplicationContext.applicationContext)
      .apiKey(apiKey)
      .stores(storesInstances)
      .userId(userId)
      .eventListener(eventListener)
      .logLevel(LogLevel.values()[logLevel])
      .observerMode(observerMode)
      .build()

    Purchasely.appTechnology = PLYAppTechnology.REACT_NATIVE

    Purchasely.start { isConfigured, error ->
      if(isConfigured) promise.resolve(true)
      else promise.reject(error)
    }

    Purchasely.purchaseListener = purchaseListener
  }

  @ReactMethod
  fun close() {
    productActivity = null
    close()
  }

  @ReactMethod
  fun getAnonymousUserId(promise: Promise) {
    promise.resolve(Purchasely.anonymousUserId)
  }

  @ReactMethod
  fun userLogin(userId: String, promise: Promise) {
    Purchasely.userLogin(userId) { refresh ->
        promise.resolve(refresh)
    }
  }

  @ReactMethod
  fun userLogout() {
    Purchasely.userLogout()
  }

  @ReactMethod
  fun setLogLevel(logLevel: Int) {
    Purchasely.logLevel = LogLevel.values()[logLevel]
  }

  @ReactMethod
  fun isReadyToPurchase(readyToPurchase: Boolean) {
    Purchasely.isReadyToPurchase = readyToPurchase
  }

  @ReactMethod
  fun setAttribute(attribute: Int, value: String) {
    Purchasely.setAttribute(Attribute.values()[attribute], value)
  }

  @ReactMethod
  fun setDefaultPresentationResultHandler(promise: Promise) {
    defaultPurchasePromise = promise
    Purchasely.setDefaultPresentationResultHandler { result, plan ->
      sendPurchaseResult(result, plan)
    }
  }

  @ReactMethod
  fun synchronize() {
    Purchasely.synchronize()
  }

  @ReactMethod
  fun presentPresentationWithIdentifier(presentationVendorId: String?,
                                        contentId: String?,
                                        promise: Promise) {
    purchasePromise = promise
    reactApplicationContext.currentActivity?.let {
      val intent = PLYProductActivity.newIntent(it)
      intent.putExtra("presentationId", presentationVendorId)
      intent.putExtra("contentId", contentId)
      it.startActivity(intent)
    }
  }

  @ReactMethod
  fun presentProductWithIdentifier(productVendorId: String,
                                   presentationVendorId: String?,
                                   contentId: String?,
                                   promise: Promise) {
    purchasePromise = promise
    reactApplicationContext.currentActivity?.let {
      val intent = PLYProductActivity.newIntent(it)
      intent.putExtra("presentationId", presentationVendorId)
      intent.putExtra("productId", productVendorId)
      intent.putExtra("contentId", contentId)
      it.startActivity(intent)
    }
  }

  @ReactMethod
  fun presentPlanWithIdentifier(planVendorId: String,
                                presentationVendorId: String?,
                                contentId: String?,
                                promise: Promise) {
    purchasePromise = promise
    reactApplicationContext.currentActivity?.let {
      val intent = PLYProductActivity.newIntent(it)
      intent.putExtra("presentationId", presentationVendorId)
      intent.putExtra("planId", planVendorId)
      intent.putExtra("contentId", contentId)
      it.startActivity(intent)
    }
  }

  @ReactMethod
  fun productWithIdentifier(vendorId: String, promise: Promise) {
    GlobalScope.launch {
      try {
        val product = Purchasely.getProduct(vendorId)
        promise.resolve(Arguments.makeNativeMap(product?.toMap() ?: emptyMap()))
      } catch (e: Exception) {
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun planWithIdentifier(vendorId: String, promise: Promise) {
    GlobalScope.launch {
      try {
        val plan = Purchasely.getPlan(vendorId)
        promise.resolve(Arguments.makeNativeMap(transformPlanToMap(plan)))
      } catch (e: Exception) {
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun allProducts(promise: Promise) {
    GlobalScope.launch {
      try {
        val products = Purchasely.allProducts()
        val result = ArrayList<ReadableMap?>()
        for (product in products) {
          result.add(Arguments.makeNativeMap(product.toMap()))
        }
        promise.resolve(Arguments.makeNativeArray(result))
      } catch (e: Exception) {
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun purchaseWithPlanVendorId(planVendorId: String, contentId: String?, promise: Promise) {
    GlobalScope.launch {
      try {
        val plan = Purchasely.plan(planVendorId)
        if(plan != null) {
          Purchasely.purchase(reactApplicationContext.currentActivity!!,
            plan,
            contentId = contentId,
            success = {
              promise.resolve(Arguments.makeNativeMap(transformPlanToMap(it)))
            },
            error = {
              promise.reject(it)
            }
          )
        } else {
          promise.reject(IllegalStateException("plan $planVendorId not found"))
        }
      } catch (e: Exception) {
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun restoreAllProducts(promise: Promise) {
    Purchasely.restoreAllProducts(
      success = {
        promise.resolve(true)
      },
      error = {
        promise.reject(it)
      }
    )
  }

  @ReactMethod
  fun displaySubscriptionCancellationInstruction() {
    Purchasely.displaySubscriptionCancellationInstruction(reactApplicationContext.currentActivity as FragmentActivity, 0)
  }

  @ReactMethod
  fun userSubscriptions(promise: Promise) {
    GlobalScope.launch {
      try {
        val subscriptions = Purchasely.getUserSubscriptions()
        val result = ArrayList<ReadableMap?>()
        for (data in subscriptions) {
          val map = data.data.toMap().toMutableMap().apply {
            this["subscriptionSource"] = when(data.data.storeType) {
              StoreType.GOOGLE_PLAY_STORE -> StoreType.GOOGLE_PLAY_STORE.ordinal
              StoreType.HUAWEI_APP_GALLERY -> StoreType.HUAWEI_APP_GALLERY.ordinal
              StoreType.AMAZON_APP_STORE -> StoreType.AMAZON_APP_STORE.ordinal
              StoreType.APPLE_APP_STORE -> StoreType.APPLE_APP_STORE.ordinal
              else -> null
            }
            if(data.data.plan == null) {
              this["plan"] = transformPlanToMap(data.plan)
            }
            this["product"] = data.product.toMap()
          }
          result.add(Arguments.makeNativeMap(map))
        }
        promise.resolve(Arguments.makeNativeArray(result))
      } catch (e: Exception) {
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun presentSubscriptions() {
    val intent = Intent(reactApplicationContext.applicationContext, PLYSubscriptionsActivity::class.java)
    reactApplicationContext.currentActivity?.startActivity(intent)
  }

  @ReactMethod
  fun handle(deeplink: String?, promise: Promise) {
    if (deeplink == null) {
      promise.reject(IllegalStateException("Deeplink must not be null"))
      return
    }
    val uri = Uri.parse(deeplink)
    promise.resolve(Purchasely.handle(uri))
  }

  @ReactMethod
  fun setLoginTappedHandler(promise: Promise) {
    Purchasely.setLoginTappedHandler { activity, refreshPresentation ->
      loginCompletionHandler = refreshPresentation
      val reactActivity = reactApplicationContext.currentActivity
      reactActivity?.startActivity(
        Intent(activity, reactActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
      )
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun onUserLoggedIn(userLoggedIn: Boolean) {
    CoroutineScope(Dispatchers.Main).launch {
      if(productActivity?.relaunch(reactApplicationContext) == false) {
        //wait for activity to relaunch
        withContext(Dispatchers.Default) { delay(500) }
      }
      productActivity?.activity?.get()?.runOnUiThread {
        loginCompletionHandler?.invoke(userLoggedIn)
      }
    }
  }

  @ReactMethod
  fun setConfirmPurchaseHandler(promise: Promise) {
    Purchasely.setConfirmPurchaseHandler { activity, processToPayment ->
      processToPaymentHandler = processToPayment
      val reactActivity = reactApplicationContext.currentActivity
      reactActivity?.startActivity(
        Intent(activity, reactActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
      )
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun processToPayment(processToPayment: Boolean) {
    CoroutineScope(Dispatchers.Main).launch {
      if(productActivity?.relaunch(reactApplicationContext) == false) {
        //wait for activity to relaunch
        withContext(Dispatchers.Default) { delay(500) }
      }
      productActivity?.activity?.get()?.runOnUiThread {
        processToPaymentHandler?.invoke(processToPayment)
      }
    }
  }

  private fun sendEvent(reactContext: ReactContext,
                        eventName: String,
                        params: WritableMap?) {
    reactContext
      .getJSModule(RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  companion object {
    var productActivity: ProductActivity? = null
    var purchasePromise: Promise? = null
    var defaultPurchasePromise: Promise? = null
    var loginCompletionHandler: PLYLoginCompletionHandler? = null
    var processToPaymentHandler: PLYProcessToPaymentHandler? = null

    fun sendPurchaseResult(result: PLYProductViewResult, plan: PLYPlan?) {
      val productViewResult = when(result) {
        PLYProductViewResult.PURCHASED -> PLYProductViewResult.PURCHASED.ordinal
        PLYProductViewResult.CANCELLED -> PLYProductViewResult.CANCELLED.ordinal
        PLYProductViewResult.RESTORED -> PLYProductViewResult.RESTORED.ordinal
      }

      val map: MutableMap<String, Any?> = HashMap()
      map["result"] = productViewResult
      map["plan"] = transformPlanToMap(plan)
      purchasePromise?.resolve(Arguments.makeNativeMap(map)) ?: defaultPurchasePromise?.resolve(Arguments.makeNativeMap(map))
    }

    private fun transformPlanToMap(plan: PLYPlan?): Map<String, Any?> {
      if(plan == null) return emptyMap()

      return plan.toMap().toMutableMap().apply {
        this["type"] = when(plan.type) {
          DistributionType.RENEWING_SUBSCRIPTION -> DistributionType.RENEWING_SUBSCRIPTION.ordinal
          DistributionType.NON_RENEWING_SUBSCRIPTION -> DistributionType.NON_RENEWING_SUBSCRIPTION.ordinal
          DistributionType.CONSUMABLE -> DistributionType.CONSUMABLE.ordinal
          DistributionType.NON_CONSUMABLE -> DistributionType.NON_CONSUMABLE.ordinal
          DistributionType.UNKNOWN -> DistributionType.UNKNOWN.ordinal
          else -> null
        }
      }
    }
  }

  class ProductActivity(
    val presentationId: String? = null,
    val productId: String? = null,
    val planId: String? = null,
    val contentId: String? = null) {

    var activity: WeakReference<PLYProductActivity>? = null

    fun relaunch(reactApplicationContext: ReactApplicationContext) : Boolean {
      val backgroundActivity = activity?.get()
      return if(backgroundActivity != null
          && !backgroundActivity.isFinishing
          && !backgroundActivity.isDestroyed) {
        reactApplicationContext.currentActivity?.let {
          it.startActivity(
            Intent(it, backgroundActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
          )
        }
        true
      } else {
        reactApplicationContext.currentActivity?.let {
          val intent = PLYProductActivity.newIntent(it)
          intent.putExtra("presentationId", presentationId)
          intent.putExtra("productId", productId)
          intent.putExtra("planId", planId)
          intent.putExtra("contentId", contentId)
          it.startActivity(intent)
        }
        return false
      }
    }
  }
}
