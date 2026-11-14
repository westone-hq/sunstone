package com.westone.sunstone.routines.dialog

/** 클래스: TripStage
 * 목적: 시연 표시를 실제 목적지 입력·검색·추천·예약 진행에 맞춘다.
 */
enum class TripStage { IDLE, DESTINATION_INPUT, SEARCHING, CHOOSING, ROUTING, RECOMMENDING, WAITING_REPLY, RESERVED, COMPLETE }
