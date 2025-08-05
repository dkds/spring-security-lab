export type Nullable<T> = T | null;
export type ResponseData = {};
export type APIError = {
  error: boolean;
  status: number;
  message: string;
};
export const isAPIError = (x: any): x is APIError => x.error !== undefined;
export class APIResponse<T extends Nullable<ResponseData>> {
  public error = false;

  private constructor(
    public data: T | null,
    public status: number,
    public message: string | null
  ) {}

  public static of<T extends ResponseData>(data: T): APIResponse<T> {
    return new APIResponse(data, 0, null);
  }
  public static ofError(status: number, message: string | null): APIResponse<null> {
    return new APIResponse(null, status, message);
  }
}
export interface AuthProvider extends ResponseData {
  name: string;
  redirectPath: string;
}
export interface AuthCodeExchangeRequest extends ResponseData {
  username: string;
  code: string;
}
export interface AuthCodeExchangeResponse extends ResponseData {
  code: string;
}
export interface SSORequest extends ResponseData {
  username: string;
  providerName: string;
}
export interface SSOResponse extends ResponseData {
  redirectLocation: string;
}
export type AuthProviderManageRequest = {
  name: string;
  metadataLocation: string;
};
export interface User extends ResponseData {
  email: string;
  role: string;
  token: string;
  providers: AuthProvider[];
}
export type UserRegistration = {
  email: string;
  password: string;
  confirmPassword: string;
};
export type UserLogin = {
  email: string;
  password: string;
};
export type UserLogoutRequest = {
  isSSO: boolean;
};
export type UserAuthProvider = {
  email: string;
  providerName: string;
};
export type AuthProviderCreateRequest = {
  name: string;
  metadataLocation: string;
};
export enum APIStatus {
  IDLE,
  LOADING,
  SUCCESS,
  ERROR,
}
