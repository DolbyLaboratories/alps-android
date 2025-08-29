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

/**
 * \file
 * \brief API for setting active AC-4 presentation from application layer.
 */

#ifndef DLB_ALPS_NATIVE_DLB_ALPS_NATIVE_H
#define DLB_ALPS_NATIVE_DLB_ALPS_NATIVE_H

#include <stddef.h>

#include "types.h"

typedef struct alps_ctx_t alps_ctx;
typedef void* callback_ctx;
typedef void (*presentations_changed_cb)(callback_ctx);

/**
 * \brief Returns library's version
 * \return pointer to a null-terminated string containing library's version
 */
char *alps_version(void);

/**
 * \brief Gives the size in bytes of the memory required by the \ref alps_init function.
 *
 * The memory of given size needs to be allocated and freed by user.
 *
 * \param[out] mem_size size in bytes of required memory
 * \return \ref ALPS_RET_E_INVALID_ARG if \p mem_size is NULL, \ref ALPS_RET_OK on success.
 */
alps_ret alps_query_mem(
    size_t *mem_size);

/**
 * \brief Initializes the library.
 *
 * \param[out] ctx pointer to hold the context of the library.
 * \param[in]  mem pointer to the memory of size as given by \ref alps_query_mem.
 * \return \ref ALPS_RET_E_INVALID_ARG if either \p ctx or \p mem is NULL, \ref ALPS_RET_OK on success.
 */
alps_ret alps_init(
    alps_ctx **ctx,
    void      *mem
);

/**
 * @brief Releases memory allocated for the library
 * @param[in] ctx pointer to the library context
 */
void alps_destroy(
    alps_ctx *ctx
);

/**
 * @brief Sets a callback function called from inside \ref alps_process_isobmff_segment when list of presentation changes.
 * @param[in] ctx pointer to the library context
 * @param[in] presentations_cb pointer to the callback function
 * @param[in] cb_ctx data passed to the callback function when called
 */
void alps_set_presentations_changed_callback(
    alps_ctx                *ctx,
    presentations_changed_cb presentations_cb,
    callback_ctx             cb_ctx
);

/**
 * @brief Returns context set via \ref alps_set_presentations_changed_callback.
 * @param[in] ctx pointer to the library context
 * @return previously set callback context or NULL if never set
 */
callback_ctx alps_get_presentations_changed_callback_context(
    alps_ctx *ctx
);

/**
 * @brief Parses list of presentations out of given ISOBMFF segment and sets the active presentation in all the AC-4
 * samples it contains.
 * @param[in] ctx pointer to the library context
 * @param[in] buffer memory buffer holding the ISOBMFF segment
 * @param[in] size size of the buffer in bytes
 * @return
 * - \ref ALPS_RET_E_INVALID_ARG if \p ctx or \p buffer is 0
 * - \ref ALPS_RET_E_PARSE if the segment or contained AC-4 samples could not be successfully parsed
 * - \ref ALPS_RET_E_NO_AC4_TRACK if no AC_4 track could be found in the segment
 * - \ref ALPS_RET_OK if the operation succeeds
 */
alps_ret alps_process_isobmff_segment(
    alps_ctx *ctx,
    unsigned char *buffer,
    size_t size
);

/**
 * @brief Retrieves array of the presentations. \ref alps_process_isobmff_segment must be called before to get
 * meaningful results. The array is guaranteed to be valid only until the next call to \ref alps_process_isobmff_segment.
 * @param[in] ctx pointer to the library context
 * @param[out] presentations pointer to the presentations array
 * @param[out] count number of elements on the array
 * @return \ref ALPS_RET_E_INVALID_ARG if any of the arguments is 0 or ALPS_RET_OK if the operation succeeds
 */
alps_ret alps_get_presentations(
    alps_ctx           *ctx,
    alps_presentation **presentations,
    size_t             *count
);

/**
 * @brief Retrieves the currently set active presentation ID
 * @param[in] ctx pointer to the library context
 * @param[out] presentation_id currently set presentation ID
 * @return \ref ALPS_RET_E_INVALID_ARG if any of the arguments is 0 or ALPS_RET_OK if the operation succeeds
 */
alps_ret alps_get_active_presentation_id(
    alps_ctx *ctx,
    int      *presentation_id
);

/**
 * @brief Sets the active presentation ID.
 * @param[in] ctx pointer to the library context
 * @param presentation_id active presentation ID; the value must be equal to the ID of the one of the presentation from
 * the array retrieved by \ref alps_get_presentations.
 * @return \ref ALPS_RET_E_INVALID_ARG if \p ctx is 0 or ALPS_RET_OK if the operation succeeds
 */
alps_ret alps_set_active_presentation_id(
    alps_ctx *ctx,
    int       presentation_id
);

#endif /* DLB_ALPS_NATIVE_DLB_ALPS_NATIVE_H */
