//
// Created by Karl Stenerud on 07.10.21.
//

#include "path_builder.h"
#include "number_to_string.h"
#include "string.h"
#include <string.h>

#define MAX_SUBPATHS 100
#define PATH_SIZE 500
// Use padding to make it easier and safer to detect overflow.
#define PATH_SIZE_PADDING 100

typedef struct {
  char path[PATH_SIZE + PATH_SIZE_PADDING];
  char *subpaths[MAX_SUBPATHS];
  int subpath_level;
} path_builder;

static path_builder g_path;

static inline size_t available_byte_count(const char *subpath) {
  return g_path.path + sizeof(g_path.path) - subpath;
}

static inline char *subpath_begin() {
  if (g_path.subpath_level >= MAX_SUBPATHS - 1) {
    return NULL;
  }

  char *subpath = g_path.subpaths[g_path.subpath_level];
  if (subpath - g_path.path >= PATH_SIZE) {
    return NULL;
  }

  if (g_path.subpath_level > 0) {
    *subpath++ = '.';
  }
  return subpath;
}

static inline void subpath_end(char *subpath) {
  *subpath = '\0';
  g_path.subpath_level++;
  g_path.subpaths[g_path.subpath_level] = subpath;
}

void bsg_pb_reset() {
  g_path.path[0] = '\0';
  g_path.subpaths[0] = g_path.path;
  g_path.subpath_level = 0;
}

void bsg_pb_stack_map_key(const char *key) {
  char *subpath = subpath_begin();
  if (subpath == NULL) {
    return;
  }
  bsg_strncpy_safe(subpath, key, available_byte_count(subpath));
  subpath += strlen(subpath);
  subpath_end(subpath);
}

void bsg_pb_stack_list_index(int64_t index) {
  char *subpath = subpath_begin();
  if (subpath == NULL) {
    return;
  }
  subpath += bsg_int64_to_string(index, subpath);
  subpath_end(subpath);
}

void bsg_pb_stack_new_list_entry() {
  char *subpath = subpath_begin();
  if (subpath == NULL) {
    return;
  }
  subpath_end(subpath);
}

void bsg_pb_unstack() {
  if (g_path.subpath_level <= 0) {
    return;
  }

  g_path.subpath_level--;
  char *subpath = g_path.subpaths[g_path.subpath_level];
  *subpath = '\0';
}

const char *bsg_pb_path() { return g_path.path; }