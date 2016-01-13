#include "libraw_descriptor_stream.h"
extern "C"
{
#include <jpeglib.h>
}
LibRaw_descriptor_datastream::LibRaw_descriptor_datastream(int source)
{
	f = fdopen(source, "rb");
}

LibRaw_descriptor_datastream::~LibRaw_descriptor_datastream()
{
	if(f)
		fclose(f);
}

int LibRaw_descriptor_datastream::valid()
{
	return f?1:0;
}

#define LR_BF_CHK() do {if(!f) throw LIBRAW_EXCEPTION_IO_EOF;}while(0)

int LibRaw_descriptor_datastream::read(void * ptr,size_t size, size_t nmemb)
{
	LR_BF_CHK();
	return substream?substream->read(ptr,size,nmemb):int(fread(ptr,size,nmemb,f));
}

int LibRaw_descriptor_datastream::eof()
{
	LR_BF_CHK();
	return substream?substream->eof():feof(f);
}

int LibRaw_descriptor_datastream:: seek(INT64 o, int whence)
{
	LR_BF_CHK();
	return substream?substream->seek(o,whence):fseeko(f,o,whence);
}

INT64 LibRaw_descriptor_datastream::tell()
{
	LR_BF_CHK();
	return substream?substream->tell():ftello(f);
}

char* LibRaw_descriptor_datastream::gets(char *str, int sz)
{
	LR_BF_CHK();
	return substream?substream->gets(str,sz):fgets(str,sz,f);
}

int LibRaw_descriptor_datastream::scanf_one(const char *fmt, void*val)
{
	LR_BF_CHK();
	return substream?substream->scanf_one(fmt,val):fscanf(f,fmt,val);
}

void *LibRaw_descriptor_datastream::make_jas_stream()
{
	return 0; // AJM: Only for Red cinema, not worrying about this for now.
}

int LibRaw_descriptor_datastream::jpeg_src(void *jpegdata)
{
	#ifdef NO_JPEG
		return -1;
	#else
		if(!f)
			return -1;
		j_decompress_ptr cinfo = (j_decompress_ptr) jpegdata;
		jpeg_stdio_src(cinfo,f);
		return 0; // OK
	#endif
}