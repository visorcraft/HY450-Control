#!/usr/bin/env bash

hy450_read_metadata() {
  local source_file="$1"
  local line key value
  local parsed_width= parsed_height= parsed_fourcc= parsed_stride=
  local seen_width=0 seen_height=0 seen_fourcc=0 seen_stride=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" != *=* ]]; then
      echo "Invalid capture metadata line" >&2
      return 1
    fi
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      width|height|stride)
        if [[ ! "$value" =~ ^[0-9]{1,5}$ ]]; then
          echo "Invalid capture metadata number: $key" >&2
          return 1
        fi
        case "$key" in
          width)
          (( seen_width == 0 )) || { echo "Duplicate capture width" >&2; return 1; }
          seen_width=1
          parsed_width=$((10#$value))
          ;;
        height)
          (( seen_height == 0 )) || { echo "Duplicate capture height" >&2; return 1; }
          seen_height=1
          parsed_height=$((10#$value))
          ;;
        stride)
          (( seen_stride == 0 )) || { echo "Duplicate capture stride" >&2; return 1; }
          seen_stride=1
          parsed_stride=$((10#$value))
          ;;
        esac
        ;;
      fourcc)
        (( seen_fourcc == 0 )) || { echo "Duplicate capture fourcc" >&2; return 1; }
        if [[ "$value" != MJPG && "$value" != YUYV ]]; then
          echo "Unsupported captured format: $value" >&2
          return 1
        fi
        seen_fourcc=1
        parsed_fourcc="$value"
        ;;
      *)
        echo "Unexpected capture metadata key: $key" >&2
        return 1
        ;;
    esac
  done < "$source_file"

  if (( seen_width == 0 || seen_height == 0 || seen_fourcc == 0 || seen_stride == 0 )); then
    echo "Incomplete capture metadata" >&2
    return 1
  fi
  if (( parsed_width == 0 || parsed_height == 0
        || parsed_width > 16384 || parsed_height > 16384 )); then
    echo "Unsupported capture dimensions: ${parsed_width}x${parsed_height}" >&2
    return 1
  fi
  if [[ "$parsed_fourcc" == YUYV ]] && (( parsed_stride != parsed_width * 2 )); then
    echo "Unsupported YUYV stride: $parsed_stride" >&2
    return 1
  fi

  width="$parsed_width"
  height="$parsed_height"
  fourcc="$parsed_fourcc"
  stride="$parsed_stride"
}
