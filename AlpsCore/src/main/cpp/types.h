/***************************************************************************************************
 *                Copyright (C) 2024-2026 by Dolby International AB.
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
 * \brief Definitions of types used throughout the library.
 */

#include <sys/types.h>

#ifndef DLB_ALPS_NATIVE_UTILS_TYPES_H
#define DLB_ALPS_NATIVE_UTILS_TYPES_H


#define ALPS_INVALID_PRES_ID ((int)(-1)) /**< value for a presentation ID that is invalid */
#define ALPS_ARDI_BOX_MISSING 255 /** value for audio_rendering_incication when there is no 'ardi' box in the preselection */

/**
 * @brief Values returned by the functions in the library
 */
typedef enum
{
    ALPS_RET_OK = 0,             /**< operation was successful */
    ALPS_RET_E_UNDEFINED,        /**< there was an error of unknown cause */
    ALPS_RET_E_INVALID_ARG,      /**< argument given to the function is invalid */
    ALPS_RET_E_BUFF_TOO_SMALL,   /**< memory of the buffer is too small to hold all the necessary data */
    ALPS_RET_E_PARSE,            /**< there was an error in parsing */
    ALPS_RET_E_NEXT_SEGMENT,     /**< the required data is available only in the next ISOBMFF segment */
    ALPS_RET_E_NO_AC4_TRACK,     /**< there's no track containing AC-4 samples in parsed ISOBMFF segment */
    ALPS_RET_E_PRES_ID_NOT_FOUND /**< presentation ID was not found amongst known presentation IDs */
} alps_ret;

/**
 * @brief Contains additional info about the presentation, like Dialogue Enhancement level.
 */
typedef struct alps_presentation_kind_t
{
    char *scheme_uri; /**< the name of the kind or naming scheme for the value */
    char *value;      /**< name of the kind from the scheme defined by scheme_uri */
} alps_presentation_kind;


/** 
 * @brief Describes a label assigned to a presentation. If \ref is_group_label is `true`, 
 *        describes a label for a group of other labels
 */
typedef struct alps_presentation_label_t
{
    u_int16_t label_id;      /** id of the label */
    char* language;          /** language of the label */
    char* label;             /** the actual label text */
    u_int8_t is_group_label; /** is this a label for the group of labels */
} alps_presentation_label;

/**
 * @brief Describes an AC-4 presentation
 */
typedef struct alps_presentation_t
{
    int id;                          /** ID of the presentation */
    char *extended_language;         /** language of the presentation */
    alps_presentation_kind *kinds;   /** array of kinds the preselection is of */
    unsigned int kinds_count;        /** number of items in kinds array */
    alps_presentation_label *labels; /** array of labels describing the presentation */
    unsigned int labels_count;       /** number of items in the labels array */
    int selection_priority;          /** selection priority of the presentation */
    int audio_rendering_indication;  /** audio rendering indication or ALPS_ARDI_BOX_MISSING */
    int dialog_gain_present;         /** signals whether the dialog_gain value is present */
    float dialog_gain;               /** represents a gain value in decibel, reflecting the perceptually subjective gain of dialogue in the mix. */
} alps_presentation;


#endif /* DLB_ALPS_NATIVE_UTILS_TYPES_H */
