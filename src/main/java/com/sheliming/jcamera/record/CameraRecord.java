package com.sheliming.jcamera.record;

import com.sheliming.jcamera.CamerStatus;
import com.sheliming.jcamera.Camera;
import com.sheliming.jcamera.CameraFactory;
import com.sheliming.jcamera.swing.RecordFrame;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraRecord {
    public static ExecutorService service = Executors.newFixedThreadPool(5);

    /**
     * 预览摄像头
     */
    public static void previewOpencv(Camera camera) {
        long startTime = System.currentTimeMillis();

        opencv_videoio.VideoCapture vc = new opencv_videoio.VideoCapture(camera.getDeviceId());
        CanvasFrame cFrame = new CanvasFrame("opencv自带", CanvasFrame.getDefaultGamma() / 2.2);

        //javacv提供的转换器，方便mat转换为Frame
        OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
        opencv_core.Mat mat = new opencv_core.Mat();


        double fpsStart = System.currentTimeMillis();
        double fpsEnd;
        while (true) {
            if (!cFrame.isVisible()) {//窗口是否关闭
                vc.release();//停止抓取
                return;
            }
            vc.retrieve(mat);//重新获取mat
            if (vc.grab()) {//是否采集到摄像头数据
                if (vc.read(mat)) {//读取一帧mat图像
                    cFrame.showImage(converter.convert(mat));
                }
                mat.release();//释放mat
            }
            fpsEnd = System.currentTimeMillis();
            System.out.println("fps:" + 1000.0 / (fpsEnd - fpsStart));
            fpsStart = fpsEnd;

            long endTime = System.currentTimeMillis();
            System.out.println("摄像头启动时间： " + (endTime - startTime) + "ms");

            //每45毫秒捕获一帧
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }

    /**
     * 预览摄像头
     */
    public static void previewJavacv(Camera camera) throws FrameGrabber.Exception, InterruptedException {
        long startTime = System.currentTimeMillis();

        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(camera.getDeviceId());
        grabber.start();   //开始获取摄像头数据
        CanvasFrame canvas = new CanvasFrame("摄像头");//新建一个窗口
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        canvas.setAlwaysOnTop(true);

        double fpsStart = System.currentTimeMillis();
        double fpsEnd;
        while (true) {
            if (!canvas.isDisplayable()) {//窗口是否关闭
                grabber.stop();//停止抓取
                System.exit(-1);//退出
            }

            Frame frame = grabber.grab();

            canvas.showImage(frame);//获取摄像头图像并放到窗口上显示， 这里的Frame frame=grabber.grab(); frame是一帧视频图像

            fpsEnd = System.currentTimeMillis();
            System.out.println("fps:" + 1000.0 / (fpsEnd - fpsStart));
            fpsStart = fpsEnd;

            long endTime = System.currentTimeMillis();
            System.out.println("摄像头启动时间： " + (endTime - startTime) + "ms");
            Thread.sleep(50);//50毫秒刷新一次图像
        }

    }

    /**
     * 打开录制界面
     *
     * @param camera
     * @return
     * @throws FrameGrabber.Exception
     * @throws InterruptedException
     */
    public static String recordFrame(Camera camera) throws FrameGrabber.Exception, InterruptedException {
        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(camera.getDeviceId());
        grabber.start();   //开始获取摄像头数据
        CanvasFrame cFrame = new CanvasFrame("摄像头");//新建一个窗口
        cFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        cFrame.setAlwaysOnTop(true);
        cFrame.setVisible(false);

        Canvas canvas = cFrame.getCanvas();
        RecordFrame recordFrame = new RecordFrame(camera, canvas, grabber);

        while (true) {
            if (!canvas.isDisplayable()) {//窗口是否关闭
                grabber.stop();//停止抓取
                System.exit(-1);//退出
            }

            Frame frame = grabber.grab();

            cFrame.showImage(frame);//获取摄像头图像并放到窗口上显示， 这里的Frame frame=grabber.grab(); frame是一帧视频图像

            Thread.sleep(50);//50毫秒刷新一次图像
        }
    }


    public static String record(Camera camera, int frameRate, OpenCVFrameGrabber grabber) {
        //把摄像头的状态设为开启
        camera.setState(CamerStatus.open);

        service.submit(new RecordThread(camera,frameRate,grabber));
        System.out.println(" ===> main Thread execute here ! ");


        return null;
    }

    public static class RecordThread implements Runnable{
        private Camera camera;
        private int frameRate;
        private OpenCVFrameGrabber grabber;

        public RecordThread(Camera camera,int frameRate,OpenCVFrameGrabber grabber){
            this.camera = camera;
            this.frameRate = frameRate;
            this.grabber = grabber;
        }

        public void run() {
            try {
                Loader.load(opencv_objdetect.class);

                OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();//转换器
                opencv_core.IplImage grabbedImage = converter.convert(grabber.grab());//抓取一帧视频并将其转换为图像，至于用这个图像用来做什么？加水印，人脸识别等等自行添加
                int width = grabbedImage.width();
                int height = grabbedImage.height();

                FrameRecorder recorder = FrameRecorder.createDefault("record123.mp4", width, height);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); // avcodec.AV_CODEC_ID_H264，编码
                recorder.setFormat("flv");//封装格式，如果是推送到rtmp就必须是flv封装格式
                recorder.setFrameRate(frameRate);

                recorder.start();//开启录制器
                long startTime = 0;
                long videoTS = 0;
//        CanvasFrame frame = new CanvasFrame("camera", CanvasFrame.getDefaultGamma() / grabber.getGamma());
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setAlwaysOnTop(true);
                org.bytedeco.javacv.Frame rotatedFrame = converter.convert(grabbedImage);//不知道为什么这里不做转换就不能推到rtmp
                int i = 0;
                while ((grabbedImage = converter.convert(grabber.grab())) != null && camera.getState() == CamerStatus.open) {
                    i++;
                    System.out.println(i);
                    rotatedFrame = converter.convert(grabbedImage);
                    if (startTime == 0) {
                        startTime = System.currentTimeMillis();
                    }
                    videoTS = 1000 * (System.currentTimeMillis() - startTime);
                    recorder.setTimestamp(videoTS);
                    recorder.record(rotatedFrame);
                    //Thread.sleep(40);
                }
                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 停止录制
     *
     * @param camera
     */
    public static void stopRecord(Camera camera) {
        if (camera.getState() != CamerStatus.open) {
            System.out.println("摄像头没有打开");
            return;
        }
        camera.setState(CamerStatus.close);
    }

    public static void main(String[] args) throws FrameGrabber.Exception, InterruptedException {
//        previewOpencv(0);
//        previewJavacv(0);
        List<Camera> cameraList = CameraFactory.getCameraList();
        recordFrame(cameraList.get(0));
    }
}
