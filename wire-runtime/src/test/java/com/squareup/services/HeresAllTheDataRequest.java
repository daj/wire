// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: ../wire-runtime/src/test/proto/rxjava_service2.proto at 5:1
package com.squareup.services;

import com.squareup.wire.Message;
import com.squareup.wire.ProtoField;
import java.lang.Object;
import java.lang.Override;
import okio.ByteString;

public final class HeresAllTheDataRequest extends Message {
  private static final long serialVersionUID = 0L;

  public static final ByteString DEFAULT_DATA = ByteString.EMPTY;

  @ProtoField(
      tag = 1,
      type = Message.Datatype.BYTES
  )
  public final ByteString data;

  public HeresAllTheDataRequest(ByteString data) {
    this.data = data;
  }

  private HeresAllTheDataRequest(Builder builder) {
    this(builder.data);
    setBuilder(builder);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof HeresAllTheDataRequest)) return false;
    return equals(data, ((HeresAllTheDataRequest) other).data);
  }

  @Override
  public int hashCode() {
    int result = hashCode;
    return result != 0 ? result : (hashCode = data != null ? data.hashCode() : 0);
  }

  public static final class Builder extends com.squareup.wire.Message.Builder<HeresAllTheDataRequest> {
    public ByteString data;

    public Builder() {
    }

    public Builder(HeresAllTheDataRequest message) {
      super(message);
      if (message == null) return;
      this.data = message.data;
    }

    public Builder data(ByteString data) {
      this.data = data;
      return this;
    }

    @Override
    public HeresAllTheDataRequest build() {
      return new HeresAllTheDataRequest(this);
    }
  }
}
