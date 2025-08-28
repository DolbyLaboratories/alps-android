/***************************************************************************************************
 *                Copyright (C) 2024 by Dolby International AB.
 *                All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *    and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific prior written
 *    permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **************************************************************************************************/

#ifndef DLB_ALPS_NATIVE_UTILS_TYPES_H
#define DLB_ALPS_NATIVE_UTILS_TYPES_H

#define ALPS_INVALID_PRES_ID ((int)(-1))

typedef enum
{
    ALPS_RET_OK = 0,
    ALPS_RET_E_UNDEFINED,
    ALPS_RET_E_INVALID_ARG,
    ALPS_RET_E_BUFF_TOO_SMALL,
    ALPS_RET_E_PARSE,
    ALPS_RET_E_NEXT_SEGMENT,
    ALPS_RET_E_NO_MOVIE_INFO,
    ALPS_RET_E_PRES_ID_NOT_FOUND
} alps_ret;

typedef struct alps_presentation_t
{
    int presentation_id;
    char *label;
    char *language;
} alps_presentation;

#endif /* DLB_ALPS_NATIVE_UTILS_TYPES_H */
