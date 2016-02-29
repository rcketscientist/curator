//package com.anthonymandra.framework;
//
//import android.content.Context;
//
//import com.anthonymandra.util.ImageUtils;
//import com.squareup.picasso.Request;
//import com.squareup.picasso.RequestHandler;
//
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//
//import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;
//
//public class RawRequestHandler extends RequestHandler
//{
//	final Context context;
//	public RawRequestHandler(Context c)
//	{
//		context = c;
//	}
//
//	@Override
//	public boolean canHandleRequest(Request data)
//	{
//		// We'll delegate supported android images to picasso core method
//		return !ImageUtils.isAndroidImage(context, data.uri);
//	}
//
//	@Override
//	public Result load(Request request, int networkPolicy) throws IOException
//	{
//		byte[] image = ImageUtils.getThumb(context, request.uri);
//		ByteArrayInputStream inStream = new ByteArrayInputStream(image);
//		return new Result(inStream, NETWORK);
//	}
//}
