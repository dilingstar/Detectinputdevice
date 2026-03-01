package com.detectinputdevice;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

// 导入 InputManager 和 UsbManager 相关的包
import android.content.Context;
import android.hardware.input.InputManager; 
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

// 导入 SharedPreferences 用于保存数据
import android.content.SharedPreferences;

// 导入读取 /proc 和 /sys 文件所需的包
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

// 导入其他辅助包
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // 1. 定义界面上的控件
    private Button btnGetInput, btnGetUsb, btnClear, btnMonitorInput;
    private TextView tvOutput;

    // 2. 定义系统服务
    // 我们仍然需要 InputManager 来获取 getDevice()
    private InputManager mInputManager; 
    private UsbManager mUsbManager;

    // 3. 定义 SharedPreferences (用于持久化保存日志)
    private SharedPreferences mPrefs;
    public static final String PREFS_NAME = "DetectInputPrefs";
    public static final String KEY_LOG_TEXT = "logText";

    // 4. 实时监控相关
    private Handler mMonitorHandler = new Handler();
    private boolean mIsMonitoring = false;
    private Runnable mStopMonitorRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        btnGetInput = findViewById(R.id.btn_get_input);
        btnGetUsb = findViewById(R.id.btn_get_usb);
        btnClear = findViewById(R.id.btn_clear);
        btnMonitorInput = findViewById(R.id.btn_monitor_input);
        tvOutput = findViewById(R.id.tv_output);

        // 初始化系统服务
        mInputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // 初始化 SharedPreferences
        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 加载上次保存的日志
        loadSavedLogs();

        // ---- 设置按钮点击事件 ----
        btnGetInput.setOnClickListener(v -> getAllInputInfo());
        btnGetUsb.setOnClickListener(v -> getUsbDevicesInfo());
        btnClear.setOnClickListener(v -> clearLogs());
        btnMonitorInput.setOnClickListener(v -> toggleInputMonitoring());

        // 初始化停止监控的 Runnable
        mStopMonitorRunnable = () -> {
            stopMonitoring();
            logToOutput("=== 监控已自动停止 (30s) ===", false);
            saveLogs();
        };
    }

    // --- 核心: 拦截所有输入事件 ---
    // (这是“实时获取输入”的真正实现)

    /**
     * 1. 拦截触摸屏事件
     * ev.toString() 包含 App 能看到的全部“原始”触摸信息
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mIsMonitoring) {
            // 实时报告，不省略
            logToOutput("[实时触摸事件] " + ev.toString(), false);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 2. 拦截键盘事件
     * event.toString() 包含 App 能看到的全部“原始”按键信息
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mIsMonitoring) {
            // 实时报告，不省略
            logToOutput("[实时键盘事件] " + event.toString(), false);
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 3. 拦截通用事件 (鼠标, 摇杆)
     * ev.toString() 包含 App 能看到的全部“原始”通用事件信息
     */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (mIsMonitoring) {
            // 实时报告，不省略
            logToOutput("[实时通用事件] " + ev.toString(), false);
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    // --- 主要功能 ---
    
    /**
     * 主要功能1: 获取所有输入设备信息 (3组)
     */
    private void getAllInputInfo() {
        
        // --- [1/3] A 计划: InputDevice (框架级) ---
        logToOutput("\n=== [1/3] A: 通过 InputDevice.getDeviceIds() (框架级) ===", true);
        logToOutput("--- 这是 App (包括游戏) 能看到的唯一列表 ---\n", false);

        int[] deviceIdsDirect = InputDevice.getDeviceIds(); 
        if (deviceIdsDirect.length == 0) {
            logToOutput("未通过 API 找到任何输入设备", false);
        } else {
            logToOutput("通过 API 找到 " + deviceIdsDirect.length + " 个输入设备:", false);
        }
        for (int deviceId : deviceIdsDirect) {
            // 注意: 这里我们还是用 InputDevice.getDevice(id)
            // 游戏也可以用 mInputManager.getInputDevice(id)，结果是一样的
            InputDevice device = InputDevice.getDevice(deviceId); 
            if (device == null) continue;
            logRawInputDeviceData(device); // 调用辅助函数打印
        }

        // --- [2/3] B 计划: /proc/bus/input/devices (内核级) ---
        logToOutput("\n=== [2/3] B: 尝试读取 /proc/bus/input/devices (内核级) ===", false);
        logToOutput("--- App (包括游戏) 通常无权读取 ---\n", false);
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/bus/input/devices"))) {
            StringBuilder procInfo = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                procInfo.append(line).append("\n");
                if (line.startsWith("S: Sysfs=") && line.contains("/virtual/")) {
                    procInfo.append("!!! 路径检测: 这是一个虚拟设备 !!!\n");
                }
            }
            logToOutput(procInfo.toString(), false);

        } catch (IOException e) {
            logToOutput(getKernelReadErrorMessage("EACCES", e.getMessage()), false);
        }

        // --- [3/3] C 计划: /sys/bus/usb/devices (内核级USB) ---
        logToOutput("\n=== [3/3] C: 尝试读取 /sys/bus/usb/devices (内核级USB) ===", false);
        logToOutput("--- App (包括游戏) 通常无权读取 ---\n", false);
        try {
            File usbSysDir = new File("/sys/bus/usb/devices");
            File[] devices = usbSysDir.listFiles();
            
            // *** 这是你指出的错误修复 ***
            if (devices == null) {
                 throw new IOException("无法列出文件 (devices == null)。这很可能是权限拒绝(EACCES)。");
            }
            
            logToOutput("读取 " + usbSysDir.getPath() + " 成功 (但可能列表为空):", false);
            int count = 0;
            for (File device : devices) {
                // 只显示真实设备/Hub，过滤掉 "1-0:1.0" 这样的接口
                if (device.isDirectory() && !device.getName().contains(":")) {
                    logToOutput(" - " + device.getName(), false);
                    count++;
                }
            }
            if (count == 0) {
                 logToOutput(" - (未找到子目录，或无权列出)", false);
            }
        } catch (Exception e) {
            logToOutput(getKernelReadErrorMessage("EACCES", e.toString()), false);
        }
        
        showToast("输入设备信息获取完毕");
        saveLogs();
    }

    /**
     * 辅助函数: 打印 InputDevice 的“最原始” API 数据
     * 这就是 App 能看到的全部内容
     */
    private void logRawInputDeviceData(InputDevice device) {
        StringBuilder rawInfo = new StringBuilder();

        rawInfo.append("\n--- 原始 API 数据 ---\n");
        rawInfo.append("Name: ").append(device.getName()).append("\n");
        rawInfo.append("Id: ").append(device.getId()).append("\n");
        rawInfo.append("Descriptor: ").append(device.getDescriptor()).append("\n");
        rawInfo.append("VendorId: ").append(device.getVendorId()).append("\n");
        rawInfo.append("ProductId: ").append(device.getProductId()).append("\n");
        
        rawInfo.append("isVirtual(): ").append(device.isVirtual()).append("\n");
        rawInfo.append("isExternal(): ").append(device.isExternal()).append("\n");
        rawInfo.append("getSources(): ").append(device.getSources()).append(" (0x").append(Integer.toHexString(device.getSources())).append(")\n");
        rawInfo.append("getKeyboardType(): ").append(device.getKeyboardType()).append(" (").append(getKeyboardTypeString(device.getKeyboardType())).append(")\n");
        rawInfo.append("getControllerNumber(): ").append(device.getControllerNumber()).append("\n");
        
        rawInfo.append("MotionRanges (").append(device.getMotionRanges().size()).append("个):\n");
        if (device.getMotionRanges().size() == 0) {
            rawInfo.append("  (此设备没有报告运动范围)\n");
        }
        for (InputDevice.MotionRange range : device.getMotionRanges()) {
            rawInfo.append("  - Axis: ").append(range.getAxis()).append(" (").append(getAxisString(range.getAxis())).append(")\n");
            rawInfo.append("    Source: ").append(range.getSource()).append(" (0x").append(Integer.toHexString(range.getSource())).append(")\n");
            rawInfo.append("    Min: ").append(range.getMin()).append("\n");
            rawInfo.append("    Max: ").append(range.getMax()).append("\n");
            rawInfo.append("    Fuzz: ").append(range.getFuzz()).append("\n");
            rawInfo.append("    Flat: ").append(range.getFlat()).append("\n");
            rawInfo.append("    Resolution: ").append(range.getResolution()).append("\n");
        }

        logToOutput(rawInfo.toString(), false);
        logToOutput("----------------------------", false); // 分隔符
    }


    /**
     * 主要功能2: 获取所有USB设备信息 (框架级)
     */
    private void getUsbDevicesInfo() {
        logToOutput("\n=== 开始通过 UsbManager API 获取 (框架级USB) ===", true);
        logToOutput("--- 仅当手机处于 OTG (主机) 模式时才会显示设备 ---\n", false);

        if (mUsbManager == null) {
            logToOutput("错误: 无法获取 UsbManager 服务", false);
            showToast("获取 UsbManager 失败");
            return;
        }

        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            logToOutput("未找到任何连接的 USB 设备 (请使用 OTG 插入设备)", false);
            showToast("未找到 USB 设备");
        } else {
            logToOutput("找到 " + deviceList.size() + " 个 USB 设备:", false);
        }

        for (UsbDevice device : deviceList.values()) {
            StringBuilder rawInfo = new StringBuilder();

            rawInfo.append("\n--- 原始 API 数据 ---\n");
            rawInfo.append("DeviceName: ").append(device.getDeviceName()).append("\n");
            rawInfo.append("VendorId: ").append(device.getVendorId()).append("\n");
            rawInfo.append("ProductId: ").append(device.getProductId()).append("\n");
            
            try {
                // 这两项有时会是 null 或抛出异常
                rawInfo.append("ManufacturerName: ").append(device.getManufacturerName()).append("\n");
                rawInfo.append("ProductName: ").append(device.getProductName()).append("\n");
            } catch (Exception e) {
                rawInfo.append("Manufacturer/Product Name: (获取失败或为空)\n");
            }
            
            int deviceClass = device.getDeviceClass();
            rawInfo.append("DeviceClass: ").append(deviceClass).append(" (").append(getUsbClassCodeString(deviceClass)).append(")\n");
            rawInfo.append("DeviceSubclass: ").append(device.getDeviceSubclass()).append("\n");
            rawInfo.append("DeviceProtocol: ").append(device.getDeviceProtocol()).append("\n");
            
            rawInfo.append("InterfaceCount: ").append(device.getInterfaceCount()).append("\n");
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                rawInfo.append("  - Interface ").append(intf.getId()).append(":\n");
                int intfClass = intf.getInterfaceClass();
                rawInfo.append("    Class: ").append(intfClass).append(" (").append(getUsbClassCodeString(intfClass)).append(")\n");
                rawInfo.append("    Subclass: ").append(intf.getInterfaceSubclass()).append("\n");
                rawInfo.append("    Protocol: ").append(intf.getInterfaceProtocol()).append("\n");
                
                rawInfo.append("    EndpointCount: ").append(intf.getEndpointCount()).append("\n");
                for (int j = 0; j < intf.getEndpointCount(); j++) {
                    UsbEndpoint ep = intf.getEndpoint(j);
                    rawInfo.append("    - Endpoint ").append(j).append(":\n");
                    rawInfo.append("      Address: ").append(ep.getAddress()).append(" (0x").append(Integer.toHexString(ep.getAddress())).append(")\n");
                    rawInfo.append("      Type: ").append(ep.getType()).append(" (").append(getEndpointTypeString(ep.getType())).append(")\n");
                    rawInfo.append("      Direction: ").append((ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT")).append("\n");
                    rawInfo.append("      MaxPacketSize: ").append(ep.getMaxPacketSize()).append("\n");
                }
            }
            
            logToOutput(rawInfo.toString(), false);
            logToOutput("----------------------------", false); // 分隔符
        }
        showToast("USB 设备信息获取完毕");
        saveLogs();
    }

    /**
     * 主要功能3: 切换实时输入监控
     */
    private void toggleInputMonitoring() {
        if (mIsMonitoring) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
    }

    private void startMonitoring() {
        mIsMonitoring = true;
        btnMonitorInput.setText("停止监控");
        logToOutput("\n=== 开始30秒监控 (实时报告) ... 请操作输入设备 ===", true);
        
        // 30秒后自动停止
        mMonitorHandler.postDelayed(mStopMonitorRunnable, 30000); 
    }

    private void stopMonitoring() {
        mIsMonitoring = false;
        mMonitorHandler.removeCallbacks(mStopMonitorRunnable); // 移除自动停止的任务
        btnMonitorInput.setText("实时获取输入");
    }


    // ---- 辅助功能函数 ----

    // 辅助: 在调试窗口打印日志
    private void logToOutput(String message, boolean clearOld) {
        if (clearOld) {
            tvOutput.setText(message + "\n");
        } else {
            tvOutput.append(message + "\n");
        }
    }

    // 辅助: 保存日志
    private void saveLogs() {
        mPrefs.edit().putString(KEY_LOG_TEXT, tvOutput.getText().toString()).apply();
    }

    // 辅助: 加载日志
    private void loadSavedLogs() {
        tvOutput.setText(mPrefs.getString(KEY_LOG_TEXT, ""));
    }

    // 辅助: 清空日志
    private void clearLogs() {
        tvOutput.setText("");
        saveLogs(); 
        showToast("日志已清空");
    }

    // 辅助: 显示 Toast
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // 辅助: 内核文件读取 错误信息
    private String getKernelReadErrorMessage(String errorType, String message) {
        if (message != null && (message.contains(errorType) || message.contains("Permission denied"))) {
            return "读取内核文件/目录失败:\n" +
                   "!!! 错误: " + errorType + " (Permission denied) !!!\n" +
                   "这是 Android 10+ 系统的正常安全限制。\n" +
                   "没有 root 权限的 App (包括游戏) 无法读取此文件。";
        } else {
            return "读取内核文件/目录失败: " + message;
        }
    }
    
    // 辅助: 翻译 键盘类型
    private String getKeyboardTypeString(int type) {
        switch (type) {
            case InputDevice.KEYBOARD_TYPE_NONE: return "NONE";
            case InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC: return "NON_ALPHABETIC";
            case InputDevice.KEYBOARD_TYPE_ALPHABETIC: return "ALPHABETIC";
            default: return "未知";
        }
    }

    // 辅助: 翻译 运动轴 (Motion Axis)
    private String getAxisString(int axis) {
        switch (axis) {
            case MotionEvent.AXIS_X: return "AXIS_X";
            case MotionEvent.AXIS_Y: return "AXIS_Y";
            case MotionEvent.AXIS_PRESSURE: return "AXIS_PRESSURE";
            case MotionEvent.AXIS_SIZE: return "AXIS_SIZE";
            case MotionEvent.AXIS_TOUCH_MAJOR: return "AXIS_TOUCH_MAJOR";
            case MotionEvent.AXIS_TOUCH_MINOR: return "AXIS_TOUCH_MINOR";
            case MotionEvent.AXIS_TOOL_MAJOR: return "AXIS_TOOL_MAJOR";
            case MotionEvent.AXIS_TOOL_MINOR: return "AXIS_TOOL_MINOR";
            case MotionEvent.AXIS_ORIENTATION: return "AXIS_ORIENTATION";
            case MotionEvent.AXIS_HSCROLL: return "AXIS_HSCROLL";
            case MotionEvent.AXIS_VSCROLL: return "AXIS_VSCROLL";
            case MotionEvent.AXIS_RUDDER: return "AXIS_RUDDER";
            case MotionEvent.AXIS_GAS: return "AXIS_GAS";
            case MotionEvent.AXIS_BRAKE: return "AXIS_BRAKE";
            default: return "其他轴 (" + axis + ")";
        }
    }

    /**
     * 辅助: 翻译 USB Class Code
     */
    private String getUsbClassCodeString(int classCode) {
        switch (classCode) {
            case UsbConstants.USB_CLASS_PER_INTERFACE: return "按接口定义 / 未定义 / 充电器 (0)";
            case UsbConstants.USB_CLASS_HID: return "键盘/鼠标/手柄 (HID)";
            case UsbConstants.USB_CLASS_MASS_STORAGE: return "U盘/存储设备";
            case UsbConstants.USB_CLASS_CDC_DATA: return "串口/通信设备 (CDC)";
            case UsbConstants.USB_CLASS_AUDIO: return "音频设备";
            
            case UsbConstants.USB_CLASS_HUB: 
                return "USB集线器 / 物理设备 (9)";

            case UsbConstants.USB_CLASS_VENDOR_SPEC: return "厂商特定 (Vendor Specific)";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER: return "无线控制器";
            case UsbConstants.USB_CLASS_VIDEO: return "视频设备";
            case UsbConstants.USB_CLASS_APP_SPEC: return "应用特定";
            case UsbConstants.USB_CLASS_STILL_IMAGE: return "相机/图像";
            case UsbConstants.USB_CLASS_PRINTER: return "打印机";
            case UsbConstants.USB_CLASS_CONTENT_SEC: return "内容安全";
            case UsbConstants.USB_CLASS_CSCID: return "智能卡";
            
            case 15: // UsbConstants.USB_CLASS_PERSONAL_HEALTHCARE (API 21)
                return "个人健康 (15)";

            default: return "其他类型 (" + classCode + ")";
        }
    }
    
    // 辅助: 翻译 USB Endpoint Type
    private String getEndpointTypeString(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL: return "控制传输 (Control)";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "同步传输 (Isochronous)";
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "批量传输 (Bulk)";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "中断传输 (Interrupt)";
            default: return "未知";
        }
    }
}