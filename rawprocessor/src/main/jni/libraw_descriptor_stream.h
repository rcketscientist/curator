#ifndef RAWDROID_LIBRAW_DESCRIPTOR_STREAM_H
#define RAWDROID_LIBRAW_DESCRIPTOR_STREAM_H

#include <libraw/libraw_datastream.h>

class DllDef LibRaw_descriptor_datastream : public LibRaw_abstract_datastream
{
	public:
		LibRaw_descriptor_datastream(int fd);
		virtual             ~LibRaw_descriptor_datastream();
		virtual int         valid();
		virtual int         jpeg_src(void *jpegdata);
		virtual void        *make_jas_stream();

		virtual int         read(void * ptr,size_t size, size_t nmemb);
		virtual int         eof();
		virtual int         seek(INT64 o, int whence);
		virtual INT64       tell();
		virtual INT64	    size() { return _fsize;}
		virtual char*       gets(char *str, int sz);
		virtual int         scanf_one(const char *fmt, void*val);
		virtual int         get_char()
		{
			#ifndef WIN32
			return substream?substream->get_char():getc_unlocked(f);
			#else
			return substream?substream->get_char():fgetc(f);
			#endif
		}

	protected:
		FILE *f;
		INT64 _fsize;
		#ifdef WIN32
		std::wstring wfilename;
		#endif
};
#endif //RAWDROID_LIBRAW_DESCRIPTORSTREAM_H
