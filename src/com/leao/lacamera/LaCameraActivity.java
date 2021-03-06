package com.leao.lacamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.Settings;
import android.util.FloatMath;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.leao.lacamera.FileListAdapter.FileInfo;
import com.leao.lacamera.util.DateUtil;
import com.leao.lacamera.util.FileUtil;
import com.leao.lacamera.util.LogUtil;
import com.leao.lacamera.util.SearchGoogleUtil;

public class LaCameraActivity extends Activity implements OnItemClickListener,
		OnClickListener {

	public static final String TAG = "LaCamera";

	public static final int PHOTOHRAPH = 1;
	 private static final String TEMP_FILE_NAME = "temp.jpg";
	 private static final String IMAGE_TEMP_DIR =
	 Environment.getExternalStorageDirectory()+"/LaCamera/temp/";
	public static final String IMAGE_DIR = Environment
			.getExternalStorageDirectory() + "/LaCamera/image/";
	public static double douLatitude = 23.098022285398542;
	public static double douLongitude = 113.2801204919815;
	public static Location currentLocation;

	protected static final int UPATE_LOCATION = 1001;
	protected static final int REFRESH = 1002;
	protected static final int MAKE_IMAGE = 1003;
	protected static final int INITFINISH = 1004;
	protected static final int LOCATION_STATUS = 1005;
	protected static final int MAKE_IMAGE_BYBITMAP = 1006;
	protected static final int GPS_STATUS = 1007;

	protected static LocationManager locationManager;
	protected static Handler mHandler;
	protected static MyLocationService gpsLocationListener;
	protected static MyLocationService networkLocationListener;

	private static double OFFSETLAT = -0.00264;
	private static double OFFSETLON = 0.00545;
	private ImageView mImageView;
	private FileListAdapter fileAdapterList;
	private FileData currentData;
	private ListView itemlist = null;
	private ArrayList<FileInfo> fInfos = new ArrayList<FileListAdapter.FileInfo>();
	private ProgressDialog mProgressDialog;
	private RelativeLayout relativeLayoutPre;
	private Button postBtn;
	private Button cancelBtn;
	private ImageView previewView;
	private TextView gpsImgView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// 获取定位服务的Manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		initComponents();
		handleMessage();
		startGPSLocationListener();
		this.getResources().getText(R.string.initialization);
		refreshListData();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Intent intentLocation = new Intent(this, MyLocationService.class);
		this.startService(intentLocation);
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		finishGPSLocationListener(); // release the GPS resources
		Intent intent = new Intent(this, MyLocationService.class);
		this.stopService(intent);
		System.exit(-1);
	}
	@Override 
    public void onConfigurationChanged(Configuration config) { 
		super.onConfigurationChanged(config); 
    } 
	
	private void handleMessage() {
		// 在MyLocationService.java中，字面可了解大概意思
		mHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case UPATE_LOCATION:
					Location location = (Location) msg.obj;
					// 开启GPS定位，在下面的onResume事件（用户可以交互时触发）中调用
					// 如果当前无定位信息，则给出默认坐标
					currentLocation = location;
					mImageView.setEnabled(true);
					break;
				case LOCATION_STATUS:
					String num = (String) msg.obj;
					Toast.makeText(LaCameraActivity.this, num, Toast.LENGTH_SHORT).show();
					gpsImgView.setText(num);
					break;
				case GPS_STATUS:
					String count = (String) msg.obj;
//	                Toast.makeText(LaCameraActivity.this, "卫星颗数："+count, Toast.LENGTH_SHORT).show();
	                gpsImgView.setText(count+"");
	                break;
				case MAKE_IMAGE:
					// 让ProgressDialog显示
					showProgressDialog(false);
					Uri tempUri = null;
					if(msg.obj == null){
						tempUri = Images.Media.EXTERNAL_CONTENT_URI;
					}else{
						tempUri =(Uri) msg.obj;
					}
					final Uri u = tempUri;
					new Thread() {

						@Override
						public void run() {
							String imageFilepath = "";
							Cursor cursor = getContentResolver().query(u, null,
									null, null, Images.Media.DATE_TAKEN+" DESC");
							cursor.moveToFirst();
							int index = cursor
									.getColumnIndex(android.provider.MediaStore.Images.Media.DATA);
							if (-1 != index) {
								imageFilepath = cursor.getString(index);// 获取文件的绝对路径
							}
							cursor.close();

							double latitude = currentLocation.getLatitude();
							double longitude = currentLocation.getLongitude();
							// 设置文件保存路径这里放在跟目录下
							// File picture = new
							// File(IMAGE_TEMP_DIR+TEMP_FILE_NAME);
							String addr = SearchGoogleUtil.getAddr(latitude,
									longitude);
//							String addr = SearchGoogleUtil.getAddr(currentLocation, LaCameraActivity.this);
							BitmapFactory.Options opts = new BitmapFactory.Options();
							opts.inSampleSize = 2;

							Bitmap bmp = BitmapFactory.decodeFile(imageFilepath, opts);
							pressText(DateUtil.getWaterDate(), String.format(
									getString(R.string.latitude_longitude),
									latitude, longitude), addr, bmp,
									"宋体", 36, Color.YELLOW, 25, 20, 0, 0x88);

							File tempFile = new File(imageFilepath);
							tempFile.delete();
//							dismissProgressDialog();

							Message msg = mHandler.obtainMessage(REFRESH);
							mHandler.sendMessage(msg);

						}

					}.start();
					break;
				case MAKE_IMAGE_BYBITMAP:
					// 让ProgressDialog显示
					showProgressDialog(false);
					final Bitmap myBitmap = (Bitmap) msg.obj;
					new Thread() {

						@Override
						public void run() {   
							double latitude = currentLocation.getLatitude()+OFFSETLAT;
							double longitude = currentLocation.getLongitude()+OFFSETLON;
							// 设置文件保存路径这里放在跟目录下
							// File picture = new
							// File(IMAGE_TEMP_DIR+TEMP_FILE_NAME);
							String addr = SearchGoogleUtil.getAddr(latitude,
									longitude);
							pressText(DateUtil.getWaterDate(), String.format(
									getString(R.string.latitude_longitude),
									latitude, longitude), addr, myBitmap,
									"宋体", 36, Color.YELLOW, 25, 20, 0, 0x88);

//							File tempFile = new File(imageFilepath);
//							tempFile.delete();
//							dismissProgressDialog();

							Message msg = mHandler.obtainMessage(REFRESH);
							mHandler.sendMessage(msg);
						}

					}.start();
					break;
				case REFRESH:
					refreshListData();
					break;
				case INITFINISH:
					initList();
					break;
				default:
					break;
				}
			}
		};
	}

	// LayoutInflater inflater;


	private void initComponents() {
		mImageView = (ImageView) findViewById(R.id.camera);
		// inflater = getLayoutInflater();

		// LayoutInflater inflater
		// =(LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
		// inflater = LayoutInflater.from(this);
		relativeLayoutPre = (RelativeLayout) findViewById(R.id.preview_layout);
		mImageView.setOnClickListener(listener);
		itemlist = (ListView) findViewById(R.id.listView);

		itemlist.setOnItemClickListener(this);
		itemlist.setOnItemLongClickListener(itemLongClickListener);
		itemlist.setOnCreateContextMenuListener(this);

		postBtn = (Button) findViewById(R.id.post_btn);
		cancelBtn = (Button) findViewById(R.id.cancel_btn);
		previewView = (ImageView) findViewById(R.id.preview_view);

		previewView.setOnTouchListener(onTouchListener);
		
		postBtn.setOnClickListener(this);
		cancelBtn.setOnClickListener(this);
		
		gpsImgView = (TextView) findViewById(R.id.gps_numtext);
	}


	Matrix matrix = new Matrix();
	private OnTouchListener onTouchListener = new OnTouchListener() {
		   Matrix savedMatrix = new Matrix(); 
		   PointF start = new PointF();
		   PointF mid = new PointF(); 
		   static final int NONE = 0;
		   static final int DRAG = 1;
		   static final int ZOOM = 2;
		   int mode = NONE; 
		   float oldDist = 1f;  
		@Override
		public boolean onTouch(View v, MotionEvent event) {
				
			      // Handle touch events here...
			      ImageView view = (ImageView) v;
			      // Handle touch events here...
			      switch (event.getAction() & MotionEvent.ACTION_MASK) {
			        //设置拖拉模式
			        case MotionEvent.ACTION_DOWN:
			            savedMatrix.set(matrix);
			            start.set(event.getX(), event.getY());
			            Log.d(TAG, "mode=DRAG" );
			            mode = DRAG;
			            break;

			         case MotionEvent.ACTION_UP:
			         case MotionEvent.ACTION_POINTER_UP:
			            mode = NONE;
			            Log.d(TAG, "mode=NONE" );
			            break;
			         //设置多点触摸模式
			         case MotionEvent.ACTION_POINTER_DOWN:
			            oldDist = spacing(event);
			            Log.d(TAG, "oldDist=" + oldDist);
			            if (oldDist > 10f) {
			               savedMatrix.set(matrix);
			               midPoint(mid, event);
			               mode = ZOOM;
			               Log.d(TAG, "mode=ZOOM" );
			            }
			            previewView.setScaleType(ScaleType.MATRIX);
			            break;
			          //若为DRAG模式，则点击移动图片
			         case MotionEvent.ACTION_MOVE:
			            if (mode == DRAG) {
			               matrix.set(savedMatrix);
			               // 设置位移
			               matrix.postTranslate(event.getX() - start.x,event.getY() - start.y);
			            }
			            //若为ZOOM模式，则多点触摸缩放
			               else if (mode == ZOOM) {
			               float newDist = spacing(event);
			               Log.d(TAG, "newDist=" + newDist);
			               if (newDist > 10f) {
			                  matrix.set(savedMatrix);
			                  float scale = newDist / oldDist;
			                  //设置缩放比例和图片中点位置
			                  matrix.postScale(scale, scale, mid.x, mid.y);
			               }
			            }
			            previewView.setScaleType(ScaleType.MATRIX);
			            break;
			      }

			      // Perform the transformation
			      view.setImageMatrix(matrix);
			     
			      return true; // indicate event was handled
			   
		}
	};
	//计算移动距离
	   private float spacing(MotionEvent event) {
	      float x = event.getX(0) - event.getX(1);
	      float y = event.getY(0) - event.getY(1);
	      return FloatMath.sqrt(x * x + y * y);
	   }
	//计算中点位置
	   private void midPoint(PointF point, MotionEvent event) {
	      float x = event.getX(0) + event.getX(1);
	      float y = event.getY(0) + event.getY(1);
	      point.set(x / 2, y / 2);
	   } 
	private void showProgressDialog(boolean isInit) {
		if (null == mProgressDialog) {
			if (isInit) {
				mProgressDialog = ProgressDialog.show(this, null,
						getString(R.string.initialization), true);
			} else {
				mProgressDialog = ProgressDialog.show(this, null,
						getString(R.string.makewater), true);
			}
		}
	}

	private void dismissProgressDialog() {
		if (mProgressDialog != null) {
			try {
				mProgressDialog.dismiss();
			} catch (Exception e) {
				e.printStackTrace();
			}
			mProgressDialog = null;
		}
	}

	private static volatile int rowId ;
	private OnItemLongClickListener itemLongClickListener = new OnItemLongClickListener() {
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view,
				int position, long id) {
//			doOpenFile(currentData.fileInfos.get(position).path);
			rowId = position;
			return false;
		}
	};


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		menu.setHeaderTitle("操作");
		menu.add(1, 101, 0, "打开");
		menu.add(1, 102, 0, "删除");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item){
		switch (item.getItemId()) {
		case 101:
			doOpenFile(currentData.fileInfos.get(rowId).path);
			break;
		case 102:
			FileUtil.deleteFile(currentData.fileInfos.get(rowId).path);
			refreshListData();
			break;
		default:
			break;
		}
		return super.onContextItemSelected(item);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (relativeLayoutPre.isShown()) {
				backList();
				return true;
			}
			finish();
		default:
			return super.onKeyDown(keyCode, event);
		}
	}

	private void backList() {
		Animation mAnimation = AnimationUtils.loadAnimation(this, R.anim.preview_fade_in);  
		relativeLayoutPre.startAnimation(mAnimation);
		mImageView.setEnabled(true);
		itemlist.setEnabled(true);
		relativeLayoutPre.setVisibility(View.INVISIBLE);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.post_btn:

			break;
		case R.id.cancel_btn:
			backList();
			break;
		default:
			break;
		}

	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		Animation mAnimation = AnimationUtils.loadAnimation(this, R.anim.preview_fade_out);  
		relativeLayoutPre.startAnimation(mAnimation);
//		relativeLayoutPre.setAnimation(mAnimation);
//		relativeLayoutPre.startLayoutAnimation();
		FileInfo fileinfo = currentData.fileInfos.get(position);
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(fileinfo.path, opts);
		int orgWidth = opts.outWidth;
		int orgHeight  = opts.outHeight;
		opts.inSampleSize =  2;
		opts.inJustDecodeBounds = false;
		Bitmap bmp = BitmapFactory.decodeFile(fileinfo.path, opts);

		previewView.setImageBitmap(bmp);
		int w = previewView.getWidth();
		int h = previewView.getHeight();
		previewView.setScaleType(ScaleType.FIT_CENTER);
		mImageView.setEnabled(false);
		itemlist.setEnabled(false);
		matrix.reset();
//		matrix.setScale((float)w/orgWidth, (float)h/orgHeight);
		relativeLayoutPre.setVisibility(View.VISIBLE);

		
		// AlertDialog.Builder builder = new AlertDialog.Builder(this);
		// builder.setTitle("发送图像")
		// .setView(img).setPositiveButton("发送", new
		// DialogInterface.OnClickListener() {
		// @Override
		// public void onClick(DialogInterface dialog, int which) {
		//
		// }
		// })
		// .setNegativeButton("取消",null);
		//
		// img.setOnTouchListener(new OnTouchListener(){
		//
		// @Override
		// public boolean onTouch(View v, MotionEvent event) {
		// Toast.makeText(LaCameraActivity.this,
		// R.string.msg_unable_to_get_current_location,
		// Toast.LENGTH_SHORT).show();
		// return false;
		// }
		//
		//
		// });
		// builder.show();

	}

	private void doOpenFile(String filePath) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri uri = Uri.parse("file://" + filePath);
		String type = null;
		type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
				MimeTypeMap.getFileExtensionFromUrl(filePath));
		if (type != null) {
			intent.setDataAndType(uri, type);
			try {
				startActivity(intent);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, getString(R.string.can_not_open_file),
						Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(
					this,
					getString(R.string.can_not_find_a_suitable_program_to_open_this_file),
					Toast.LENGTH_SHORT).show();
		}

	}

	private void initList() {
		currentData = new FileData(fInfos, null, IMAGE_DIR);
		fileAdapterList = new FileListAdapter(this, currentData);
		itemlist.setAdapter(fileAdapterList);
		fileAdapterList.notifyDataSetChanged();
		dismissProgressDialog();
	}

	private void refreshListData() {
		findFileInfo(IMAGE_DIR, fInfos);
		//initList();
	}

	/**
	 * 
	 * @param path
	 * @param list
	 */
	private void findFileInfo(final String path, final List<FileInfo> list) {
		showProgressDialog(true);

		new Thread() {
			@Override
			public void run() {
				synchronized (list) {
					list.clear();

					File base = new File(path);
					File[] files = base.listFiles();
					if (files != null && files.length != 0){
						String name;
						int length = files.length;
						for (int i = 0; i < length; i++) {
							File file = files[i];
							name = file.getName();
							// if (files[i].isHidden()) {
							// continue;
							// }
							long time = file.lastModified();
							list.add(new FileInfo(name, file.getAbsolutePath(),
									FileUtil.switchIcon(file),
									null, // fileSize(files[i].length()),
									file.isDirectory(), FileUtil.getDesc(file),
									time)); // //date.toLocaleString(),
							
						}
						Collections.sort(list);
					}

					Message message = mHandler.obtainMessage();
					message.what = INITFINISH;
					mHandler.sendMessage(message);
				}
			}
		}.start();

	}
	String imagePath = "";
	private OnClickListener listener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.camera:
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				 File file = new File(IMAGE_TEMP_DIR);
				 if(!file.exists()){
					 file.mkdirs();
				 }
//			      SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//			      String filename = timeStampFormat.format(new Date());
//			     ContentValues values = new ContentValues();
//			     values.put(Media.TITLE, filename);
//			Uri photoUri = getContentResolver().insert(
//			                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
//			imagePath = getRealPathFromURI(photoUri,
//			                                        getContentResolver());
//			intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
//				 intent.putExtra(MediaStore.Images.Media.ORIENTATION, 0);
				 intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new
				 File(file,TEMP_FILE_NAME)));
//				 intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
//				 android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				startActivityForResult(intent, PHOTOHRAPH);
				break;
			default:
				break;
			}

		}
	};
	public static String getRealPathFromURI(Uri uri, ContentResolver resolver) {

        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = resolver.query(uri, proj, null, null, null);
        int column_index = cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String str = cursor.getString(column_index);
        cursor.close();

        return str;

}
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {

		if (resultCode == 0)
			return;
		// 拍照
		if (requestCode == PHOTOHRAPH) {
//			String path = "";
//            
//            Cursor cursor = getContentResolver().query( Images.Media.EXTERNAL_CONTENT_URI, new String[]{Images.Media.DATA},
//                            null, null, Images.Media.DATE_TAKEN+" DESC");
//            if(cursor!=null && cursor.moveToFirst()){
//                   
//                     path = cursor.getString(cursor.getColumnIndexOrThrow(Images.Media.DATA));
//                           
//            }
			try {
				Uri u = null;
				if(data == null || data.getData() == null){
					File f = new File(IMAGE_TEMP_DIR+TEMP_FILE_NAME);
					u = Uri.parse(android.provider.MediaStore.Images.Media.insertImage(getContentResolver(),
							f.getAbsolutePath(), null, null));
				}else{
					u = data.getData();
				}
				// Message message = new Message();

				// message.sendToTarget();
				// mHandler.obtainMessage(REFRESH).sendToTarget();
				mHandler.sendMessage(Message.obtain(mHandler, MAKE_IMAGE,u));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * 文字水印
	 * 
	 * @param pressText
	 *            水印文字
	 * @param targetImg
	 *            目标图片
	 * @param fontName
	 *            字体名称
	 * @param fontStyle
	 *            字体样式
	 * @param white
	 *            字体颜色
	 * @param fontSize
	 *            字体大小
	 * @param x
	 *            修正值
	 * @param y
	 *            修正值
	 * @param alpha
	 *            透明度
	 */
	private void pressText(String pressText, String locationText, String addr,
			Bitmap targetImg, String fontName, int fontStyle, int color,
			int fontSize, int x, int y, int alpha) {
		if (null == targetImg ) {
			return;
		}
		try {
			Bitmap bmp = targetImg;
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			Bitmap mbmpTest = Bitmap
					.createBitmap(width, height, Config.RGB_565);
			Canvas canvasTemp = new Canvas(mbmpTest);
			Typeface font = Typeface.create(fontName, fontStyle);

			Paint p = new Paint();
			canvasTemp.drawBitmap(bmp, 0, 0, p);

			p.setColor(color);
			p.setTypeface(font);
			p.setTextSize(fontSize);
			// p.setAlpha(alpha);
			canvasTemp.drawText(pressText, x, (height - fontSize * 3) + y, p);
			canvasTemp
					.drawText(locationText, x, (height - fontSize * 2) + y, p);
			canvasTemp.drawText(addr, x, (height - fontSize) + y, p);
			File imageFileDir = new File(IMAGE_DIR);
			if (!imageFileDir.exists()) {
				imageFileDir.mkdirs();
			}
			OutputStream bos = new FileOutputStream(new File(imageFileDir,
					DateUtil.getImageDate() + ".jpg"));
			bmp.recycle();
			mbmpTest.compress(CompressFormat.JPEG, 80, bos);
			mbmpTest.recycle();
			bos.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void startGPSLocationListener() {
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		criteria.setSpeedRequired(false);
		criteria.setBearingRequired(false);
		criteria.setCostAllowed(true);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		String provider = locationManager.getBestProvider(criteria, true); // gps
		if (provider != null) {
			Location location = locationManager.getLastKnownLocation(provider);
			if (null == location) {
				location = new Location("");
				location.setLatitude(douLatitude);
				location.setLongitude(douLongitude);
			}
			currentLocation = location;
			mImageView.setEnabled(true);
		} else { // gps and network are both disabled
			Toast.makeText(this, R.string.msg_unable_to_get_current_location,
					Toast.LENGTH_SHORT).show();
			new AlertDialog.Builder(this){}.setTitle("GPS设置")
			.setNegativeButton("取消", null)
			.setPositiveButton("确定", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
				       Intent intent = new Intent();  
				        intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);  
				        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  
				        try   
				        {  
				            startActivity(intent);  
				        } catch(ActivityNotFoundException ex)   
				        {  
				            intent.setAction(Settings.ACTION_SETTINGS);  
				            try {  
				                   startActivity(intent);  
				            } catch (Exception e) {  
				            }  
				        } 
					
				}
			}).show();
			mImageView.setEnabled(false);
		}

		/* GPS_PROVIDER */
		if (gpsLocationListener == null) {
			gpsLocationListener = new MyLocationService();
			// LocationManager.GPS_PROVIDER = "gps"
		}
		provider = LocationManager.GPS_PROVIDER;
		locationManager.requestLocationUpdates(provider,
				MyLocationService.MINTIME, MyLocationService.MINDISTANCE,
				gpsLocationListener);
		// Log.i("Location", provider + " requestLocationUpdates() " +
		// minTime + " " + minDistance);
		
		//增加GPS状态监听器
		locationManager.addGpsStatusListener(gpsLocationListener);

		/* NETWORK_PROVIDER */
//		if (networkLocationListener == null) {
//			networkLocationListener = new MyLocationService();
//
//			// LocationManager.NETWORK_PROVIDER = "network"
//			provider = LocationManager.NETWORK_PROVIDER;
//			locationManager.requestLocationUpdates(provider,
//					MyLocationService.MINTIME, MyLocationService.MINDISTANCE,
//					networkLocationListener);
//			// Log.i("Location", provider + " requestLocationUpdates() " +
//			// minTime + " " + minDistance);
//		}
	}

	protected  void finishGPSLocationListener() {
		if (locationManager != null) {
			if (networkLocationListener != null) {
				locationManager.removeUpdates(networkLocationListener);
			}
			if (gpsLocationListener != null) {
				locationManager.removeUpdates(gpsLocationListener);
				locationManager.removeGpsStatusListener(gpsLocationListener);
			}
			networkLocationListener = null;
			gpsLocationListener = null;
		}
	}
}

class FileData {
	public ArrayList<FileInfo> fileInfos;
	public ArrayList<Integer> selectedId;
	public String path;
	public boolean searchingTag = false;

	public FileData(ArrayList<FileInfo> fileInfos,
			ArrayList<Integer> selectedId, String path) {
		if (fileInfos == null)
			this.fileInfos = new ArrayList<FileListAdapter.FileInfo>();
		else
			this.fileInfos = fileInfos;
		if (selectedId == null)
			this.selectedId = new ArrayList<Integer>();
		else
			this.selectedId = selectedId;
		if (path == null)
			this.path = "/sdcard";
		else
			this.path = path;
	}
}