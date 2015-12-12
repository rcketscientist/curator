#ifndef SWAB_H
#define SWAB_H

#include <stdlib.h>
#include <asm/byteorder.h>

inline void swab(const void *from, void*to, ssize_t n)
{
	ssize_t i;

	if (n < 0)
		return;

	for (i = 0; i < (n / 2) * 2; i += 2)
		//*((uint16_t*)to + i) = __arch__swab16(*((uint16_t*)from + i));	//Worked in ndk9c, doesn't exist in 10d
		*((uint16_t*)to + i) = ___constant_swab16(*((uint16_t*)from + i)); 	//Exists in 10d, (Test Images worked)
}
#endif
