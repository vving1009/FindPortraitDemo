package com.example.vc.findportraitdemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import id.zelory.compressor.Compressor;
import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;

//import android.support.media.ExifInterface;

public class MainActivity extends Activity implements OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_SELECT_PIC = 120;
    private static final int MAX_FACE_NUM = 5;//最大可以检测出的人脸数量
    Cursor cursor;
    int fileColumn;
    int screenWidth, screenHeight;
    private int realFaceNum = 0;//实际检测出的人脸数量
    private Button selectBtn;
    private Button detectBtn;
    private ImageView image;
    private ProgressDialog pd;
    private Bitmap bm;//选择的图片的Bitmap对象
    private Paint paint;//画人脸区域用到的Paint
    Bitmap bitmap = null;

    private boolean hasDetected = false;//标记是否检测到人脸

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Log.d(TAG, "onCreate: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        initView();

        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);//设置话出的是空心方框而不是实心方块

        pd = new ProgressDialog(this);
        pd.setTitle("提示");
        pd.setMessage("正在检测，请稍等");

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        Log.d(TAG, "onCreate: screenWidth=" + screenWidth + ",screenHeight=" + screenHeight);

        String[] columns = {MediaStore.Images.Media.DATA};
        cursor = new CursorLoader(this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, MediaStore.Images.Media.SIZE +" DESC").loadInBackground();
        /*cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null ,MediaStore.Images.Media.MIME_TYPE + "=? or "+MediaStore.Images.Media.MIME_TYPE + "=? or "
                        + MediaStore.Images.Media.MIME_TYPE + "=?",
                new String[] { "image/jpeg","image/png" ,"image/bmp"},
                MediaStore.Images.Media.SIZE +" DESC");*/
        fileColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

    }

    /**
     * 控件初始化
     */
    private void initView() {
        selectBtn = findViewById(R.id.btn_select);
        selectBtn.setOnClickListener(this);
        detectBtn = findViewById(R.id.btn_detect);
        detectBtn.setOnClickListener(this);
        image = findViewById(R.id.image);
    }

    /**
     * 从图库选择图片
     */
    private void selectPicture() {
        Intent intent = new Intent();
        //intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setAction(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_SELECT_PIC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_PIC && resultCode == Activity.RESULT_OK) {
            //获取选择的图片
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String selectedImagePath = cursor.getString(columnIndex);
            bm = BitmapFactory.decodeFile(selectedImagePath);
            //要使用Android内置的人脸识别，需要将Bitmap对象转为RGB_565格式，否则无法识别
            bm = bm.copy(Bitmap.Config.RGB_565, true);
            cursor.close();
            image.setImageBitmap(bm);
            hasDetected = false;
        }
    }

    /**
     * 检测人脸
     */
    private void detectFace() {
        if (bm == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasDetected) {
            Toast.makeText(this, "已检测出人脸", Toast.LENGTH_SHORT).show();
        } else {
            new FindFaceTask().execute();
        }
    }

    private void drawFacesArea(Face[] faces) {
        Toast.makeText(this, "图片中检测到" + realFaceNum + "张人脸", Toast.LENGTH_SHORT).show();
        float eyesDistance;//两眼间距
        Canvas canvas = new Canvas(bm);
        for (Face face : faces) {
            if (face != null) {
                PointF pointF = new PointF();
                face.getMidPoint(pointF);//获取人脸中心点
                eyesDistance = face.eyesDistance();//获取人脸两眼的间距
                //画出人脸的区域
                canvas.drawRect(pointF.x - eyesDistance, pointF.y - eyesDistance, pointF.x + eyesDistance, pointF.y + eyesDistance, paint);
                hasDetected = true;
                int dd = (int) (eyesDistance);
                Rect faceRect = new Rect((int) (pointF.x - dd), (int) (pointF.y - dd), (int) (pointF.x + dd), (int) (pointF.y + dd));
                if (checkFace(faceRect)) {

                }
            }
            //画出人脸区域后要刷新ImageView
            image.invalidate();
        }
    }

    public boolean checkFace(Rect rect) {
        int w = rect.width();
        int h = rect.height();
        int s = w * h;
        Log.i(TAG, "人脸 宽w = " + w + "高h = " + h + "人脸面积 s = " + s);
        if (s < 10000) {
            Log.i(TAG, "无效人脸，舍弃.");
            return false;
        } else {
            Log.i(TAG, "有效人脸，保存.");
            return true;
        }
    }

    @Override
    public void onClick(View arg0) {
        switch (arg0.getId()) {
            case R.id.btn_select://选择图片
                //selectPicture();

                loadPicture();
                break;
            case R.id.btn_detect://检测人脸
                detectFace();
                break;
        }
    }

    private void loadPicture() {

        if (cursor.moveToNext()) {
            String imageFilePath = cursor.getString(fileColumn);
            //Bitmap bmp = BitmapFactory.decodeFile(imageFilePath);
            bm = getbitMap(imageFilePath);
            bm = bm.copy(Bitmap.Config.RGB_565, true);
            Log.d(TAG, "loadPicture: bmp size="+bm.getByteCount() / 1024 / 1024);
            Log.d(TAG, "loadPicture: bmp width="+bm.getWidth() + ",height="+bm.getHeight());
            image.setImageBitmap(bm);
        }
    }

    private Bitmap getbitMap(String imageFilePath) { //加载图像的尺寸而不是图像本身
        /*BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFilePath, options);
        Log.d(TAG, "getbitMap: imageFilePath=" + imageFilePath);
        Log.d(TAG, "getbitMap: options.outWidth=" + options.outWidth + ",options.outHeight=" + options.outHeight);*/
        /*int widthRatio = (int) Math.ceil(options.outWidth / (float) DISPLAY_WIDTH);
        int heightRatio = (int) Math.ceil(options.outHeight / (float) DISPLAY_HEIGHT);
        Log.v("HEIGHTRATIO", "" + heightRatio);
        Log.v("WIDTHRATIO", "" + widthRatio); //如果两个比例都大于1，那么图像的一条边将大于屏幕
        if (heightRatio > 1 && widthRatio > 1) {
            options.inSampleSize = Math.max(heightRatio, widthRatio);
        }*/
        //对它进行真正的解码
        //options.inJustDecodeBounds = false; // 此处为false，不只是解码
        //Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath, options); //修复图片方向

        /*options.inSampleSize = 1;
        if (widthRatio > 1 || heightRatio > 1) {
            if (widthRatio > heightRatio) {
                options.inSampleSize = widthRatio;
            } else {
                options.inSampleSize = heightRatio;
            }
        }*/
//https://www.2cto.com/kf/201706/647898.html
        //https://blog.csdn.net/adam_ling/article/details/52346741
        /*Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath); //修复图片方向
        Matrix m = repairBitmapDirection(imageFilePath);
        if (m != null) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        Log.d(TAG, "bitMap size= " + bitmap.getByteCount() / 1024);*/


        /*Luban.with(this)
                .load(imageFilePath)
                .ignoreBy(100)
                //.setTargetDir(getCacheDir().getAbsolutePath()+"/temp.jpg")
                .setTargetDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath())
                *//*.filter(new CompressionPredicate() {
                    @Override
                    public boolean apply(String path) {
                        return !(TextUtils.isEmpty(path) || path.toLowerCase().endsWith(".gif"));
                    }
                })*//*
                .setCompressListener(new OnCompressListener() {
                    @Override
                    public void onStart() {
                        // TODO 压缩开始前调用，可以在方法内启动 loading UI
                    }

                    @Override
                    public void onSuccess(File file) {
                        // TODO 压缩成功后调用，返回压缩后的图片文件
                        //Log.d(TAG, "onSuccess: "+file.getAbsolutePath());
                        //bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    }

                    @Override
                    public void onError(Throwable e) {
                        // TODO 当压缩过程出现问题时调用
                        e.printStackTrace();
                    }
                })
                .launch();*/
        File file = null;
        try {
            file = new Compressor(this)
                    .setMaxWidth(640)
                    .setMaxHeight(480)
                    .setQuality(75)
                    .setCompressFormat(Bitmap.CompressFormat.JPEG)
                    .setDestinationDirectoryPath(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES).getAbsolutePath())
                    .compressToFile(new File(imageFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getbitMap: path="+file.getAbsolutePath());
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    /**
     * 识别图片方向 * @param filepath * @return
     */
    private Matrix repairBitmapDirection(String filepath) { //根据图片的filepath获取到一个ExifInterface的对象
        ExifInterface exif;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException e) {
            e.printStackTrace();
            exif = null;
        }
        int degree = 0;
        if (exif != null) {
            // 读取图片中相机方向信息
            int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            // 计算旋转角度
            switch (ori) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
                default:
                    degree = 0;
                    break;
            }
        }
        if (degree != 0) { // 旋转图片
            Matrix m = new Matrix();
            m.postRotate(degree);
            return m;
        }
        return null;
    }

    /**
     * 检测图像中的人脸需要一些时间，所以放到AsyncTask中去执行
     *
     * @author yubo
     */
    private class FindFaceTask extends AsyncTask<Void, Void, Face[]> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd.show();
        }

        @Override
        protected Face[] doInBackground(Void... arg0) {
            //最关键的就是下面三句代码
            FaceDetector faceDetector = new FaceDetector(bm.getWidth(), bm.getHeight(), MAX_FACE_NUM);
            Face[] faces = new Face[MAX_FACE_NUM];
            realFaceNum = faceDetector.findFaces(bm, faces);
            if (realFaceNum > 0) {
                return faces;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Face[] result) {
            super.onPostExecute(result);

            pd.dismiss();
            if (result == null) {
                Toast.makeText(MainActivity.this, "抱歉，图片中未检测到人脸", Toast.LENGTH_SHORT).show();
            } else {
                drawFacesArea(result);
            }
        }
    }

    /*
    * 当我们需要删除暂存区或分支上的文件, 同时工作区也不需要这个文件了, 可以使用
1 git rm file_path
2 git commit -m 'delete somefile'4
3 git push
当我们需要删除暂存区或分支上的文件, 但本地又需要使用, 只是不希望这个文件被版本控制, 可以使用
git rm -rf --cached file_path
git commit -m 'delete remote somefile'
git push

https://www.cnblogs.com/weilantiankong/p/6144443.html
https://www.cnblogs.com/pyer/p/4752770.html

https://github.com/Curzibn/Luban
https://github.com/zetbaitsu/Compressor
https://blog.csdn.net/yinzhijiezhan/article/details/70139316
https://blog.csdn.net/qq_25412055/article/details/53878655
https://blog.csdn.net/copy_yuan/article/details/51353629
    *
    * */
}




