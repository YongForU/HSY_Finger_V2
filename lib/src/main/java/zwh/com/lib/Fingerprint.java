package zwh.com.lib;

import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.soter.wrapper.BuildConfig;
import com.tencent.soter.wrapper.SoterWrapperApi;
import com.tencent.soter.wrapper.wrap_callback.SoterProcessAuthenticationResult;
import com.tencent.soter.wrapper.wrap_callback.SoterProcessCallback;
import com.tencent.soter.wrapper.wrap_callback.SoterProcessKeyPreparationResult;
import com.tencent.soter.wrapper.wrap_core.SoterProcessErrCode;
import com.tencent.soter.wrapper.wrap_fingerprint.SoterFingerprintCanceller;
import com.tencent.soter.wrapper.wrap_fingerprint.SoterFingerprintStateCallback;
import com.tencent.soter.wrapper.wrap_net.IWrapUploadSignature;
import com.tencent.soter.wrapper.wrap_task.AuthenticationParam;

import io.reactivex.observers.DisposableObserver;
import zwh.com.lib.Soter.model.ConstantsSoterDemo;
import zwh.com.lib.Soter.model.DemoLogger;
import zwh.com.lib.Soter.model.DemoUtil;
import zwh.com.lib.Soter.model.SoterDemoData;
import zwh.com.lib.Soter.net.RemoteAuthentication;
import zwh.com.lib.Soter.net.RemoteGetChallengeStr;
import zwh.com.lib.Soter.net.RemoteUploadASK;
import zwh.com.lib.Soter.net.RemoteUploadPayAuthKey;
import zwh.com.lib.config.Constants;
import zwh.com.lib.config.PreferenceUtil;

import static android.app.Activity.RESULT_OK;

/**
 * Created by Luosl on 2018/7/17 0017 11:32
 * E-Mail Address：584648246@qq.com
 */
public class Fingerprint {

    private static RxFingerPrinter rxfingerPrinter;
    private static Dialog mPasswordDialog = null;
    private static SoterFingerprintCanceller mCanceller = null;
    private static Dialog mFingerprintDialog = null;
    private static View mCustomFingerprintView = null;
    private static Animation mFlashAnimation = null;
    private static TextView mFingerprintStatusHintView = null;
    private static ProgressDialog mLoadingDialog = null;
    private static String rt_msg;

    public static void setNumPassword(String numPassword){

        //设置密码
        PreferenceUtil.commitString(Constants.PASSWORD,numPassword);

    }

    public static void FingerprintVail(final Context mContext, final FingerPrinterView fingerPrinterView, final FingerprintCallback fingerprintCallback) {

        mCustomFingerprintView = LayoutInflater.from(mContext).inflate(R.layout.fingerprint_layout, null);
        mFingerprintStatusHintView = (TextView) mCustomFingerprintView.findViewById(R.id.error_hint_msg);

        fingerPrinterView.startScaning();
        
        fingerPrinterView.setOnStateChangedListener(new FingerPrinterView.OnStateChangedListener() {
            @Override public void onChange(int state) {
                if (state == FingerPrinterView.STATE_CORRECT_PWD) {
                    fingerprintCallback.success();
                }
                if (state == FingerPrinterView.STATE_WRONG_PWD) {
                    rt_msg = "指纹识别失败，请重试";
                    fingerprintCallback.fail(rt_msg);
                }


            }
        });
        rxfingerPrinter = new RxFingerPrinter((Activity) mContext);
        rxfingerPrinter.setLogging(BuildConfig.DEBUG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  //系统版本大于等于6.0
            String rom= Rom.getName();
            if(rom.equals("EMUI")){  //华为用google
                DisposableObserver<Boolean> observer = new DisposableObserver<Boolean>() {
                    @Override
                    protected void onStart() {
                        if (fingerPrinterView.getState() == FingerPrinterView.STATE_SCANING) {
                            return;
                        } else if (fingerPrinterView.getState() == FingerPrinterView.STATE_CORRECT_PWD
                                || fingerPrinterView.getState() == FingerPrinterView.STATE_WRONG_PWD) {
                            fingerPrinterView.setState(FingerPrinterView.STATE_NO_SCANING);
                        } else {
                            fingerPrinterView.setState(FingerPrinterView.STATE_SCANING);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if(e instanceof FPerException){
                            Toast.makeText(mContext,((FPerException) e).getDisplayMessage(),Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if(aBoolean){
                            fingerPrinterView.setState(FingerPrinterView.STATE_CORRECT_PWD);
                        }else{
                            fingerPrinterView.setState(FingerPrinterView.STATE_WRONG_PWD);
                        }
                    }
                };
                rxfingerPrinter.dispose();
                rxfingerPrinter.begin().subscribe(observer);
                rxfingerPrinter.addDispose(observer);
            }else {  //soter

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doUseFingerprintPayment(mContext,fingerprintCallback);
                    }
                },1000);

            }


        }else{
            rt_msg = "系统版本低于6.0";
            fingerprintCallback.fail(rt_msg);
        }


    }



    private static void doPrepareAuthKey(final Context mContext,String num, final IOnAuthKeyPrepared onAuthKeyPreparedCallback) {
//        showPasswordInputDialog(mContext,new IOnConfirmedPassword() {
//            @Override
//            public void onConfirmPassword(final String pwdDigest) {
//                // We strongly recommend you to use password to open any fingerprint related business scene.
//                // e.g., payment password/pin to open fingerprint
//
//            }
//        }, new DialogInterface.OnCancelListener() {
//            @Override
//            public void onCancel(DialogInterface dialog) {
////                DemoLogger.i("---", "soterdemo: user cancelled open in input");
//            }
//        });
        showLoading(mContext,mContext.getString(R.string.app_loading_preparing_open_keys));
        prepareAuthKey(num, onAuthKeyPreparedCallback);
    }

    private static void prepareAuthKey(final String pwdDigest, final IOnAuthKeyPrepared callback) {
        SoterWrapperApi.prepareAuthKey(new SoterProcessCallback<SoterProcessKeyPreparationResult>() {
            @Override
            public void onResult(@NonNull SoterProcessKeyPreparationResult result) {
//                DemoLogger.i("---", "soterdemo: prepare result: %s, auth key result: %s", result, result.getExtData() != null ? result.getExtData().toString() : null);
                if (result.errCode == SoterProcessErrCode.ERR_OK) {
                    if (callback != null) {
                        callback.onResult(pwdDigest, true);
                    }
                } else {
                    if (callback != null) {
                        callback.onResult(pwdDigest, false);
                    }
                }
            }
        }, false, true, ConstantsSoterDemo.SCENE_PAYMENT, new RemoteUploadPayAuthKey(pwdDigest), new RemoteUploadASK());
    }






    private static void doUseFingerprintPayment(final Context mContext, final FingerprintCallback fingerprintCallback) {
        DemoLogger.i("---", "soterdemo: user request use fingerprint payment");
        startFingerprintAuthentication(mContext,fingerprintCallback,new SoterProcessCallback<SoterProcessAuthenticationResult>() {
            @Override
            public void onResult(@NonNull SoterProcessAuthenticationResult result) {
//                DemoLogger.d("---", "soterdemo: use fingerprint payment result: %s, signature data is: %s", result.toString(), result.getExtData() != null ? result.getExtData().toString() : null);
                dismissLoading();
                if (result.isSuccess()) {
                    fingerprintCallback.success();
                } else {
                    // 先判断是否是指纹密钥失效。如果指纹失效，则重新生成并上传authkey，然后直接使用密码支付
                    if (result.errCode == SoterProcessErrCode.ERR_AUTHKEY_NOT_FOUND
                            || result.errCode == SoterProcessErrCode.ERR_AUTHKEY_ALREADY_EXPIRED || result.errCode == SoterProcessErrCode.ERR_ASK_NOT_EXIST
                            || result.errCode == SoterProcessErrCode.ERR_SIGNATURE_INVALID) {
//                        DemoLogger.w("---", "soterdemo: auth key expired or keys not found. regen and upload");
//                        Toast.makeText(mContext, "设置验证密码",
//                                Toast.LENGTH_SHORT).show();
                        startPrepareAuthKeyAndAuthenticate(mContext,fingerprintCallback);
                    } else if (result.errCode == SoterProcessErrCode.ERR_USER_CANCELLED) {
                        rt_msg = "用户取消验证";
                        fingerprintCallback.fail(rt_msg);
                    } else if (result.errCode == SoterProcessErrCode.ERR_FINGERPRINT_LOCKED) {
                        rt_msg = "多次指纹验证错误，使用密码验证";
                        fingerprintCallback.fail(rt_msg);
                        startNormalPasswordAuthentication(mContext,fingerprintCallback);
                    } else {
                        DemoLogger.w("---", "soterdemo: unknown error in doUseFingerprintPayment : %d", result.errCode);
                        if(result.errCode ==18){
                            Intent intent =  new Intent(Settings.ACTION_SETTINGS);
                            mContext.startActivity(intent);
                        }

                    }
                }
            }
        }, mContext.getString(R.string.app_use_fingerprint_pay), new RemoteAuthentication());
    }



    private static void startPrepareAuthKeyAndAuthenticate(final Context mContext,final FingerprintCallback fingerprintCallback) {
        String num = PreferenceUtil.getString(Constants.PASSWORD,"");
        doPrepareAuthKey(mContext,num,new IOnAuthKeyPrepared() {
            @Override
            public void onResult(String passwordDigestUsed, boolean isSuccess) {
                if (isSuccess) {
//                    DemoLogger.i("---", "准备authkey成功!通过密码直接进行身份验证");
                    // 重新生成并上传authkey成功，直接使用密码认证
                    PreferenceUtil.commitString(Constants.PASSWORD,passwordDigestUsed);

                    RemoteAuthentication normalPasswordAuthentication = new RemoteAuthentication(passwordDigestUsed, new RemoteAuthentication.IOnNormalPaymentCallback() {
                        @Override
                        public void onPayEnd(boolean isSuccess) {
                            dismissLoading();
                            if (isSuccess) {
                                Toast.makeText(mContext, "设置密码成功", Toast.LENGTH_SHORT).show();
                                doUseFingerprintPayment(mContext,fingerprintCallback);
                            } else {
                                Toast.makeText(mContext, "设置错误，查看日志了解更多信息",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    normalPasswordAuthentication.execute();
                } else {
                    DemoLogger.w("---", "用户在输入中取消了");
                }
            }
        });
    }

    private static void startNormalPasswordAuthentication(final Context mContext,final FingerprintCallback fingerprintCallback) {
        showPasswordInputDialog(mContext,new IOnConfirmedPassword() {
            @Override
            public void onConfirmPassword(final String pwdDigest) {
                showLoading(mContext,mContext.getString(R.string.app_verifying));
                RemoteAuthentication normalPasswordAuthentication = new RemoteAuthentication(pwdDigest, new RemoteAuthentication.IOnNormalPaymentCallback() {
                    @Override
                    public void onPayEnd(boolean isSuccess) {
                        dismissLoading();
                        String i = PreferenceUtil.getString(Constants.PASSWORD,"");
                        if(PreferenceUtil.getString(Constants.PASSWORD,"").equals(pwdDigest)){
                            fingerprintCallback.success();

                        }else {
                            rt_msg = "验证错误";
//                            rt_msg = "300004";
                            fingerprintCallback.fail(rt_msg);
                        }
                    }
                });
                normalPasswordAuthentication.execute();
            }
        }, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                DemoLogger.i("---", "用户在输入中取消了");
            }
        });
    }


    /**
     * 关闭一项业务的时候，除了业务状态之外，切记删除掉本机密钥，以及后台将原本密钥删除或者标记为不可用
     */
    private static void doCloseFingerprintPayment(Context mContext) {
        SoterWrapperApi.removeAuthKeyByScene(ConstantsSoterDemo.SCENE_PAYMENT);
        SoterDemoData.getInstance().setIsFingerprintPayOpened(mContext, false);
    }
    private static void stop_Finger(){
        cancelFingerprintAuthentication();
        // 建议在onPause的时候结束掉SOTER相关事件。当然，也可以选择自己管理，但是会更加复杂
        SoterWrapperApi.tryStopAllSoterTask();
        dismissCurrentDialog();
        dismissLoading();
    }


    // Simulate only. In real business scenes, you should check password format first
    private static void showPasswordInputDialog(Context mContext, final IOnConfirmedPassword onConfirm, DialogInterface.OnCancelListener onCancel) {
        dismissCurrentDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.input_pay_password));
        final EditText input = new EditText(mContext);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});  //密码长度
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入6位数字密码");
        builder.setView(input);
        builder.setPositiveButton(mContext.getString(R.string.app_confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
               if(input.getText().length()==6){
                   if (onConfirm != null) {
                       String pwdText = input.getText().toString();

                       onConfirm.onConfirmPassword(!DemoUtil.isNullOrNil(pwdText)
                               ? pwdText : null);
                   }
               }
            }
        });


        builder.setOnCancelListener(onCancel);
        mPasswordDialog = builder.show();
        mPasswordDialog.setCanceledOnTouchOutside(false);
    }

    private static void startFingerprintAuthentication(final Context mContext, final FingerprintCallback fingerprintCallback, SoterProcessCallback<SoterProcessAuthenticationResult> processCallback,
                                                       final String title, IWrapUploadSignature uploadSignatureWrapper) {
        DemoLogger.i("---", "soterdemo: start authentication: title: %s", title);
        dismissCurrentDialog();
        if (mCanceller != null) {
            DemoLogger.w("---", "soterdemo: last canceller is not null. should not happen because we will set it to null every time we finished the process");
            mCanceller = null;
        }
        mCanceller = new SoterFingerprintCanceller();
        // 认证逻辑部分
        showLoading(mContext,mContext.getString(R.string.app_request_challenge));
        // Prepare authentication parameters
        AuthenticationParam param = new AuthenticationParam.AuthenticationParamBuilder() // 通过Builder来构建认证请求
                .setScene(ConstantsSoterDemo.SCENE_PAYMENT) // 指定需要认证的场景。必须在init中初始化。必填
                .setContext(mContext) // 指定当前上下文。必填。
                .setFingerprintCanceller(mCanceller) // 指定当前用于控制指纹取消的控制器。当因为用户退出界面或者进行其他可能引起取消的操作时，需要开发者通过该控制器取消指纹授权。建议必填。
                .setIWrapGetChallengeStr(new RemoteGetChallengeStr()) // 用于获取挑战因子的网络封装结构体。如果在授权之前已经通过其他模块拿到后台挑战因子，则可以改为调用setPrefilledChallenge。如果两个方法都没有调用，则会引起错误。
                .setPrefilledChallenge("prefilled challenge") // 如果之前已经通过其他方式获取了挑战因子，则设置此字段。如果设置了该字段，则忽略获取挑战因子网络封装结构体的设置。如果两个方法都没有调用，则会引起错误。
                .setIWrapUploadSignature(uploadSignatureWrapper) // 用于上传最终结果的网络封装结构体。该结构体一般来说不独立存在，而是集成在最终授权网络请求中，该请求实现相关接口即可。选填，如果没有填写该字段，则要求应用方自行上传该请求返回字段。
                .setSoterFingerprintStateCallback(new SoterFingerprintStateCallback() { // 指纹回调仅仅用来更新UI相关，不建议在指纹回调中进行任何业务操作。选填。

                    // 指纹回调仅仅用来更新UI相关，不建议在指纹回调中进行任何业务操作
                    // Fingerprint state callbacks are only used for updating UI. Any logic operation is not welcomed.
                    @Override
                    public void onStartAuthentication() {
//                        DemoLogger.d("---", "soterdemo: start authentication. dismiss loading");
                        dismissLoading();
                        showFingerprintDialog(mContext,title);
                    }

                    @Override
                    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
//                        DemoLogger.w("---", "soterdemo: onAuthenticationHelp: %d, %s", helpCode, helpString);
                        // 由于厂商实现不同，不建议在onAuthenticationHelp中做任何操作。
                    }

                    @Override
                    public void onAuthenticationSucceed() {
//                        DemoLogger.d("---", "授权成功");
                        mCanceller = null;
                        // 可以在这里做相应的UI操作
                        showLoading(mContext,mContext.getString(R.string.app_verifying));
                        dismissCurrentDialog();
                    }

                    @Override
                    public void onAuthenticationFailed() {
//                        DemoLogger.w("---", "soterdemo: onAuthenticationFailed once:");
                        setFingerprintHintMsg(mContext,mContext.getString(R.string.fingerprint_normal_hint), true);
//                        "指纹验证错误，再试一次"
                        rt_msg = "指纹验证错误，再试一次";
                        fingerprintCallback.fail(rt_msg);
                    }

                    @Override
                    public void onAuthenticationCancelled() {
//                        DemoLogger.d("---", "soterdemo: user cancelled authentication");
                        mCanceller = null;
                        dismissCurrentDialog();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errorString) {
//                        DemoLogger.w("---", "soterdemo: onAuthenticationError: %d, %s", errorCode, errorString);
                        mCanceller = null;
//                        Toast.makeText(mContext, errorString, Toast.LENGTH_LONG).show();
                        dismissCurrentDialog();
                    }
                }).build();
        SoterWrapperApi.requestAuthorizeAndSign(processCallback, param);
    }

    private static void showFingerprintDialog(Context mContext, String title) {
        if (mFingerprintDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext).setTitle(title).setCancelable(true)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            cancelFingerprintAuthentication();
                            dismissCurrentDialog();
                        }
                    }).setNegativeButton(mContext.getString(R.string.app_cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            cancelFingerprintAuthentication();
                            dismissCurrentDialog();
                        }
                    }).setView(mCustomFingerprintView);
            mFingerprintDialog = builder.create();
            mFingerprintDialog.setCanceledOnTouchOutside(false);
        } else {
            setFingerprintHintMsg(mContext,"", false);
            mFingerprintDialog.setTitle(title);
        }
        mFingerprintDialog.show();
    }

    private static void setFingerprintHintMsg(Context mContext, String msg, boolean isFlash) {
//        mFingerprintStatusHintView = (TextView) mCustomFingerprintView.findViewById(R.id.error_hint_msg);
        if (mCustomFingerprintView != null) {
            mFingerprintStatusHintView.setText(msg);
            if (isFlash) {
                mFlashAnimation = AnimationUtils.loadAnimation(mContext, R.anim.anim_flash);
                mFingerprintStatusHintView.startAnimation(mFlashAnimation);
            }
        }
    }

    private static void dismissCurrentDialog() {
        if (mPasswordDialog != null && mPasswordDialog.isShowing()) {
            mPasswordDialog.dismiss();
        }
        if (mFingerprintDialog != null && mFingerprintDialog.isShowing()) {
            mFingerprintDialog.dismiss();
        }
    }

    private static void showLoading(Context mContext, String wording) {
           if(mContext!=null){
               if (mLoadingDialog == null) {
                   mLoadingDialog = ProgressDialog.show(mContext, "", wording, true, false, null);
               } else if (!mLoadingDialog.isShowing()) {
                   mLoadingDialog.setMessage(wording);
                   mLoadingDialog.show();
               } else {
                   DemoLogger.d("---", "soterdemo: already showing. change title only");
                   mLoadingDialog.setMessage(wording);
               }
           }
    }

    private static void dismissLoading() {
        if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
            mLoadingDialog.dismiss();
        }
    }

    private interface IOnConfirmedPassword {
        void onConfirmPassword(String pwdDigest);
    }

    private interface IOnAuthKeyPrepared {
        void onResult(String passwordDigestUsed, boolean isSuccess);
    }

//        private void updateUseFingerprintPayBtnStatus() {
//        if (SoterDemoData.getInstance().getIsFingerprintPayOpened()) {
//            mUseFingerprintPay.setEnabled(true);
//        } else {
//            mUseFingerprintPay.setEnabled(false);
//        }
//    }
    private static void cancelFingerprintAuthentication() {
        if (mCanceller != null) {
            mCanceller.asyncCancelFingerprintAuthentication();
        }
    }


//    @Override
//    protected void onPause() {
//        super.onPause();
//        // 确保在onPause的时候结束指纹监听，以免影响其他模块以及应用
//        cancelFingerprintAuthentication();
//        // 建议在onPause的时候结束掉SOTER相关事件。当然，也可以选择自己管理，但是会更加复杂
//        SoterWrapperApi.tryStopAllSoterTask();
//        dismissCurrentDialog();
//        dismissLoading();
//    }






}
